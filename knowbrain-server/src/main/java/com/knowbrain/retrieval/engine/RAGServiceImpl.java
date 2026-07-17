package com.knowbrain.retrieval.engine;

import com.knowbrain.agent.SearchKnowledgeTool;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.common.RAGMetrics;
import com.knowbrain.common.RequestContext;
import com.knowbrain.statistics.SearchLog;
import com.knowbrain.statistics.SearchLogMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识库问答实现 — 混合检索 + LLM 生成 + 溯源
 * 从 EICS V2.0 迁移适配，检索链路升级为 Dense + BM25 混合检索
 */
@Slf4j
@Service
public class RAGServiceImpl implements RAGService {

    private final HybridSearchService hybridSearchService;
    private final ChatClient chatClient;
    private final QueryPreprocessor preprocessor;
    private final RAGCacheService cacheService;
    private final SearchLogMapper searchLogMapper;
    private final RequestContext requestContext;
    private final RAGMetrics metrics;

    private final boolean agentEnabled;

    @Value("classpath:prompts/rag-system.txt")
    private Resource promptTemplate;

    @Value("classpath:prompts/agent-system.txt")
    private Resource agentPromptTemplate;

    @Value("${rag.confidence.high-threshold:0.8}")
    private double confidenceHighThreshold;

    @Value("${rag.confidence.low-threshold:0.6}")
    private double confidenceLowThreshold;

    public RAGServiceImpl(HybridSearchService hybridSearchService,
                          ChatClient.Builder chatClientBuilder,
                          QueryPreprocessor preprocessor,
                          RAGCacheService cacheService,
                          SearchLogMapper searchLogMapper,
                          RequestContext requestContext,
                          RAGMetrics metrics,
                          Environment environment) {
        this.hybridSearchService = hybridSearchService;
        this.chatClient = chatClientBuilder.build();
        this.preprocessor = preprocessor;
        this.cacheService = cacheService;
        this.searchLogMapper = searchLogMapper;
        this.requestContext = requestContext;
        this.metrics = metrics;
        this.agentEnabled = "true".equalsIgnoreCase(
                environment.getProperty("rag.agent.enabled", "false"));
    }

    @Override
    public ChatResponse chat(String question) {
        return chat(question, Collections.emptyList(), Collections.emptyList(), null);
    }

    @Override
    public ChatResponse chat(String question, List<Long> spaceIds) {
        return chat(question, spaceIds, Collections.emptyList(), null);
    }

    @CircuitBreaker(name = "llm-chat", fallbackMethod = "chatFallback")
    @Retryable(retryFor = Exception.class, maxAttempts = 2, backoff = @Backoff(delay = 500))
    @Override
    public ChatResponse chat(String question, List<Long> spaceIds, List<Map<String, String>> history, String category) {
        return chat(question, spaceIds, history, category, false);
    }

    @CircuitBreaker(name = "llm-chat", fallbackMethod = "chatFallback")
    @Retryable(retryFor = Exception.class, maxAttempts = 2, backoff = @Backoff(delay = 500))
    @Override
    public ChatResponse chat(String question, List<Long> spaceIds, List<Map<String, String>> history, String category, boolean skipFaq) {
        long tStart = System.currentTimeMillis();
        long tPreprocess = tStart, tCacheCheck = tStart, tSearch = tStart, tLlm = tStart, tCacheWrite = tStart;
        var requestSample = io.micrometer.core.instrument.Timer.start();

        // 0. Redis 缓存检查（有历史或分类过滤时不缓存）
        if (history.isEmpty() && (category == null || category.isBlank())) {
            ChatResponse cached = cacheService.get(question, spaceIds);
            if (cached != null) {
                metrics.recordCacheHit();
                requestSample.stop(metrics.requestTimer());
                log.debug("[RAG耗时] cache=hit total={}ms question=\"{}\"",
                        System.currentTimeMillis() - tStart, question);
                return cached;
            }
        }
        tCacheCheck = System.currentTimeMillis();

        // 1. 预处理：术语改写 + FAQ 精确匹配
        QueryPreprocessor.Result pre = preprocessor.preprocess(question);
        tPreprocess = System.currentTimeMillis();

        // 2. FAQ 命中 → 直接返回预设答案（短路，不依赖 LLM）
        //    评测模式下跳过 FAQ 短路，强制走完整检索+LLM 流水线
        if (!skipFaq && pre.isFaqMatched()) {
            metrics.recordFaqHit();
            var faqSource = new ChatResponse.SourceInfo(
                    "📋 知识库预设问答", null, null,
                    pre.faqQuestion() + " — 该回答来自知识库预设的高频问答，由人工维护更新");
            ChatResponse response = new ChatResponse(pre.faqAnswer(), List.of(faqSource), false, "high");
            int total = (int)(System.currentTimeMillis() - tStart);
            saveSearchLog(question, pre.faqAnswer(), 1, "high", true, spaceIds, category, total, null);
            requestSample.stop(metrics.requestTimer());
            log.debug("[RAG耗时] preprocess={}ms total={}ms faq=hit question=\"{}\"",
                    tPreprocess - tCacheCheck, total, question);
            return response;
        }

        // 2.5. Agent 检索智能体分叉（FAQ 未命中时）
        //      评测时 FAQ 已在步骤 2 被 skipFaq 短路——Agent 是否启用独立由 rag.agent.enabled 控制
        if (agentEnabled) {
            return agentChat(question, spaceIds, history, category, pre);
        }

        String processed = pre.rewrittenQuery();

        // 3. 混合检索（自适应 topK：复杂/多步查询扩大检索量，覆盖跨文档需求）
        var searchSample = io.micrometer.core.instrument.Timer.start();
        List<SearchResult> sources = hybridSearchService.search(processed, adaptiveTopK(question), spaceIds, category);
        searchSample.stop(metrics.searchTimer());
        tSearch = System.currentTimeMillis();

        // 4. 无结果 → 兜底
        if (sources.isEmpty()) {
            metrics.recordEmpty();
            ChatResponse response = new ChatResponse(
                    "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。",
                    Collections.emptyList(), true, "low");
            int total = (int)(System.currentTimeMillis() - tStart);
            saveSearchLog(question, response.getAnswer(), 0, "low", false, spaceIds, category, total, null);
            requestSample.stop(metrics.requestTimer());
            log.debug("[RAG耗时] cache={}ms preprocess={}ms search={}ms total={}ms results=0 question=\"{}\"",
                    tCacheCheck - tStart, tPreprocess - tCacheCheck, tSearch - tPreprocess, total, question);
            return response;
        }

        // 5. 拼接上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SearchResult s = sources.get(i);
            context.append("【参考资料").append(i + 1).append("】\n")
                    .append(s.getContent()).append("\n\n");
        }

        // 6. 加载 Prompt 模板 + 调用 LLM（流式聚合，比阻塞调用更稳定）
        String prompt = buildPrompt(context.toString(), question, history);
        String answer;
        var llmSample = io.micrometer.core.instrument.Timer.start();
        try {
            answer = chatClient.prompt().user(prompt)
                    .stream()
                    .content()
                    .collectList()
                    .map(list -> String.join("", list))
                    .block(Duration.ofSeconds(120));
            llmSample.stop(metrics.llmTimer());
            if (answer == null || answer.isEmpty()) {
                throw new BizException(502, "LLM 流式聚合结果为空（超时或 API 无响应）");
            }
        } catch (Exception e) {
            llmSample.stop(metrics.llmTimer());
            metrics.recordFallback();
            log.error("LLM 调用失败，降级返回检索原文: {}", e.getMessage());
            long tDegrade = System.currentTimeMillis();
            String fallback = buildFallbackAnswer(sources);
            List<ChatResponse.SourceInfo> fallbackSources = sources.stream()
                    .map(s -> new ChatResponse.SourceInfo(
                            s.getDocumentTitle(), s.getDocumentId(),
                            s.getChunkIndex(), s.getContent()))
                    .toList();
            ChatResponse response = new ChatResponse(fallback, fallbackSources, true, "low");
            int total = (int)(System.currentTimeMillis() - tStart);
            saveSearchLog(question, response.getAnswer(), sources.size(), "low", false, spaceIds, category, total, collectSourceTitles(sources));
            requestSample.stop(metrics.requestTimer());
            log.debug("[RAG耗时] cache={}ms preprocess={}ms search={}ms llm=FAIL({}ms) degrade={}ms total={}ms results={} fallback=true question=\"{}\"",
                    tCacheCheck - tStart, tPreprocess - tCacheCheck, tSearch - tPreprocess,
                    tDegrade - tSearch, System.currentTimeMillis() - tDegrade, total, sources.size(), question);
            return response;
        }
        tLlm = System.currentTimeMillis();

        // 7. 构造溯源 + 置信度（低置信度时过滤掉不相关的溯源，避免展示噪音）
        String confidence = evaluateConfidence(sources, answer);

        List<ChatResponse.SourceInfo> sourceInfos;
        if ("low".equals(confidence)) {
            sourceInfos = List.of();
        } else {
            sourceInfos = sources.stream()
                    .map(s -> new ChatResponse.SourceInfo(
                            s.getDocumentTitle(), s.getDocumentId(),
                            s.getChunkIndex(), s.getContent()))
                    .toList();
        }

        ChatResponse response = new ChatResponse(answer, sourceInfos, false, confidence);

        // 8. 写入 Redis 缓存（仅无历史 + medium+ 非降级回答）
        if (history.isEmpty()) {
            cacheService.put(question, spaceIds, response);
        }
        tCacheWrite = System.currentTimeMillis();

        // 9. 写入搜索日志
        metrics.recordSuccess();
        requestSample.stop(metrics.requestTimer());
        int total = (int)(System.currentTimeMillis() - tStart);
        saveSearchLog(question, answer, sources.size(), confidence, false, spaceIds, category, total, collectSourceTitles(sources));

        log.debug("[RAG耗时] cache={}ms preprocess={}ms search={}ms llm={}ms cache_write={}ms total={}ms results={} confidence={} question=\"{}\"",
                tCacheCheck - tStart, tPreprocess - tCacheCheck, tSearch - tPreprocess,
                tLlm - tSearch, tCacheWrite - tLlm, total, sources.size(), confidence, question);

        return response;
    }

    @Override
    public StreamContext chatStream(String question, List<Long> spaceIds) {
        return chatStream(question, spaceIds, Collections.emptyList(), null);
    }

    @Override
    public StreamContext chatStream(String question, List<Long> spaceIds, List<Map<String, String>> history, String category) {
        long startMs = System.currentTimeMillis();
        var requestSample = io.micrometer.core.instrument.Timer.start();

        // 0. Redis 缓存检查（有历史或分类过滤时跳过缓存）
        if (history.isEmpty() && (category == null || category.isBlank())) {
            ChatResponse cached = cacheService.get(question, spaceIds);
            if (cached != null) {
                metrics.recordCacheHit();
                requestSample.stop(metrics.requestTimer());
                List<SearchResult> cachedSources = cached.getSources() != null
                        ? cached.getSources().stream()
                            .map(s -> new SearchResult(s.getText(), s.getTitle(),
                                    s.getDocumentId(), s.getChunkIndex(), 1.0))
                            .toList()
                        : List.of();
                return new StreamContext(
                        Flux.just(cached.getAnswer()),
                        cachedSources, false, cached.getConfidence());
            }
        }

        // 1. 预处理
        QueryPreprocessor.Result pre = preprocessor.preprocess(question);

        // 2. FAQ 命中 → 短路返回预设答案，附虚拟溯源
        if (pre.isFaqMatched()) {
            metrics.recordFaqHit();
            requestSample.stop(metrics.requestTimer());
            SearchResult faqSource = new SearchResult(
                    pre.faqQuestion() + " — 该回答来自知识库预设的高频问答，由人工维护更新",
                    "📋 知识库预设问答", null, null, 1.0);
            saveSearchLog(question, pre.faqAnswer(), 1, "high", true, spaceIds, category, (int)(System.currentTimeMillis() - startMs), null);
            return new StreamContext(
                    Flux.just(pre.faqAnswer()),
                    List.of(faqSource), false, "high");
        }

        // 2.5 Agent 检索智能体分叉（FAQ 未命中时）
        //      先尝试 Agent 多步检索，失败则降级到标准流式管线
        if (agentEnabled) {
            StreamContext agentResult = agentChatStream(question, spaceIds, history, category,
                    pre, startMs, requestSample);
            if (agentResult != null) {
                return agentResult;
            }
            // Agent 失败 → 继续走标准流式管线（降级）
        }

        String processed = pre.rewrittenQuery();

        // 3. 混合检索（自适应 topK：复杂/多步查询扩大检索量，覆盖跨文档需求）
        var searchSample = io.micrometer.core.instrument.Timer.start();
        List<SearchResult> sources = hybridSearchService.search(processed, adaptiveTopK(question), spaceIds, category);
        searchSample.stop(metrics.searchTimer());

        // 4. 无结果 → 兜底
        if (sources.isEmpty()) {
            metrics.recordEmpty();
            requestSample.stop(metrics.requestTimer());
            String fallbackMsg = "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。";
            saveSearchLog(question, fallbackMsg, 0, "low", false, spaceIds, category, (int)(System.currentTimeMillis() - startMs), null);
            return new StreamContext(
                    Flux.just(fallbackMsg),
                    Collections.emptyList(), true, "low");
        }

        // 5. 拼接上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SearchResult s = sources.get(i);
            context.append("【参考资料").append(i + 1).append("】\n")
                    .append(s.getContent()).append("\n\n");
        }

        String prompt = buildPrompt(context.toString(), question, history);

        // 6. LLM 流式生成
        var llmSample = io.micrometer.core.instrument.Timer.start();
        Flux<String> tokenFlux = chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnComplete(() -> {
                    llmSample.stop(metrics.llmTimer());
                    metrics.recordSuccess();
                    requestSample.stop(metrics.requestTimer());
                })
                .onErrorResume(e -> {
                    llmSample.stop(metrics.llmTimer());
                    metrics.recordFallback();
                    requestSample.stop(metrics.requestTimer());
                    log.error("LLM 流式调用失败，降级返回检索原文: {}", e.getMessage());
                    String fb = buildFallbackAnswer(sources);
                    return Flux.just(fb);
                });

        String confidence = evaluateConfidence(sources, null);

        // 低置信度时过滤掉不相关的溯源，避免展示噪音
        List<SearchResult> effectiveSources = "low".equals(confidence) ? List.of() : sources;

        // 记录搜索日志（流式场景下答案尚未生成，记录检索阶段信息）
        saveSearchLog(question, null, sources.size(), confidence, false, spaceIds, category, (int)(System.currentTimeMillis() - startMs), collectSourceTitles(sources));

        return new StreamContext(tokenFlux, effectiveSources, false, confidence);
    }

    @Override
    public List<SearchResult> search(String question, int topK) {
        return search(question, topK, Collections.emptyList());
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1000))
    @Override
    public List<SearchResult> search(String question, int topK, List<Long> spaceIds) {
        return hybridSearchService.search(question, topK, spaceIds, null);
    }

    // ==================== 辅助方法 ====================

    /**
     * 自适应检索量 — 复杂/多步查询扩大 topK 以覆盖跨文档需求
     */
    private int adaptiveTopK(String question) {
        return isComplexQuery(question) ? 10 : 5;
    }

    /**
     * 检测查询是否需要跨文档/多步信息整合。
     * 判断依据：长度、多问号、顺序/步骤关键词。
     */
    private boolean isComplexQuery(String question) {
        if (question == null) return false;
        int len = question.length();
        if (len > 30) return true;
        int firstQ = question.indexOf('？');
        if (firstQ >= 0 && question.indexOf('？', firstQ + 1) >= 0) return true;
        for (String kw : List.of("顺序", "步骤", "先", "然后", "之后", "流程", "先后", "综合")) {
            if (question.contains(kw)) return true;
        }
        return false;
    }

    /**
     * CircuitBreaker 降级方法：LLM 熔断时跳过生成，直接返回检索原文
     */
    @SuppressWarnings("unused")
    private ChatResponse chatFallback(String question, List<Long> spaceIds,
                                       List<Map<String, String>> history, String category,
                                       Throwable t) {
        log.warn("LLM CircuitBreaker 熔断降级: {}", t.getMessage());
        List<SearchResult> sources = hybridSearchService.search(question, adaptiveTopK(question), spaceIds, category);
        String fallback = buildFallbackAnswer(sources);
        List<ChatResponse.SourceInfo> fallbackSources = sources.stream()
                .map(s -> new ChatResponse.SourceInfo(
                        s.getDocumentTitle(), s.getDocumentId(),
                        s.getChunkIndex(), s.getContent()))
                .toList();
        return new ChatResponse(fallback, fallbackSources, true, "low");
    }

    /**
     * LLM 不可用时，拼接检索到的相关原文作为降级回答
     */
    private String buildFallbackAnswer(List<SearchResult> sources) {
        if (sources.isEmpty()) {
            return "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ AI 生成服务暂时不可用，以下为检索到的相关原文：\n\n");
        for (int i = 0; i < sources.size(); i++) {
            SearchResult s = sources.get(i);
            sb.append("【").append(i + 1).append("】").append(s.getDocumentTitle()).append("\n");
            sb.append(s.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String buildPrompt(String context, String question, List<Map<String, String>> history) {
        String historyText = formatHistory(history);
        try {
            String template = new String(promptTemplate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return template.replace("{history}", historyText)
                    .replace("{context}", context)
                    .replace("{question}", question);
        } catch (IOException e) {
            log.warn("Prompt 模板加载失败，使用默认模板", e);
            return """
                    你是一个企业智能知识库助手。请根据以下参考资料回答用户问题。
                    要求：
                    1. 仅基于参考资料回答，不要编造信息
                    2. 如果参考资料不足以回答，请明确说明"当前文档库中未找到相关信息"
                    3. 回答简洁、清晰、专业
                    4. 引用具体文档时，使用 [来源: 文档名] 标注
                    5. 结合对话历史理解用户意图，回答时保持上下文连贯

                    %s
                    参考资料：
                    %s

                    用户问题：%s
                    """.formatted(historyText, context, question);
        }
    }

    /**
     * 格式化对话历史为 Prompt 文本
     * 最多取最近 10 条消息（5 轮对话）
     */
    private String formatHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        // 只取最近 N 条，防止 token 超限
        int maxMessages = 10;
        List<Map<String, String>> recent = history;
        if (history.size() > maxMessages) {
            recent = history.subList(history.size() - maxMessages, history.size());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("对话历史：\n");
        for (Map<String, String> msg : recent) {
            String role = msg.get("role");
            String content = msg.get("content");
            if (content == null || content.isBlank()) continue;
            // 截断过长的历史消息，防止 token 超限
            String truncated = content.length() > 500 ? content.substring(0, 500) + "..." : content;
            if ("user".equals(role)) {
                sb.append("用户：").append(truncated).append("\n");
            } else if ("assistant".equals(role)) {
                sb.append("助手：").append(truncated).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    // ==================== 搜索日志 ====================

    /**
     * 异步写入搜索日志（不阻塞主流程）
     */
    private void saveSearchLog(String question, String answer, int sourcesCount, String confidence,
                               boolean faqMatched, List<Long> spaceIds, String category, int costMs,
                               String sourceTitles) {
        try {
            SearchLog entry = new SearchLog();
            entry.setQuestion(question != null && question.length() > 500
                    ? question.substring(0, 500) : question);
            entry.setAnswer(answer != null && answer.length() > 500
                    ? answer.substring(0, 500) : answer);
            entry.setSourcesCount(sourcesCount);
            entry.setConfidence(confidence);
            entry.setFaqMatched(faqMatched ? 1 : 0);
            entry.setUserId(requestContext.getCurrentUserId());
            entry.setSpaceIds(spaceIds != null && !spaceIds.isEmpty()
                    ? spaceIds.stream().map(String::valueOf).collect(Collectors.joining(",")) : null);
            entry.setCategory(category);
            entry.setCostMs(costMs);
            entry.setSourceTitles(sourceTitles);
            searchLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("搜索日志写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 从检索结果中提取文档标题（去重，逗号分隔）
     */
    private String collectSourceTitles(List<SearchResult> sources) {
        if (sources == null || sources.isEmpty()) return null;
        return sources.stream()
                .map(SearchResult::getDocumentTitle)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 简单置信度判断（基于检索质量）：
     * - high: 至少 3 条结果，最高相似度 > 0.8
     * - medium: 至少 2 条结果，最高相似度 > 0.6
     * - low: 其余情况
     *
     * @param answer 可选：当非 null 且为空时判为 low（流式场景传入 null 跳过此检查）
     */
    private String evaluateConfidence(List<SearchResult> sources, String answer) {
        if (sources.isEmpty()) return "low";
        // 非流式场景：answer 明确为空时降级
        if (answer != null && answer.isEmpty()) return "low";

        double maxScore = sources.stream()
                .mapToDouble(SearchResult::getScore)
                .max()
                .orElse(0.0);

        if (sources.size() >= 3 && maxScore > confidenceHighThreshold) return "high";
        if (sources.size() >= 2 && maxScore > confidenceLowThreshold) return "medium";
        return "low";
    }

    // ==================== Agent 检索智能体 ====================

    /**
     * Agent 模式：LLM 通过 searchKnowledge 工具自主多步检索。
     *
     * <p>异常时降级到标准 RAG 管线，确保可用性不降级。
     */
    private ChatResponse agentChat(String question, List<Long> spaceIds,
                                   List<Map<String, String>> history,
                                   String category, QueryPreprocessor.Result pre) {
        long tStart = System.currentTimeMillis();

        // 1. 创建本次请求的检索工具（携带 spaceIds / category 权限过滤）
        var searchTool = new SearchKnowledgeTool(
                hybridSearchService, spaceIds, category);

        // 2. 加载 Prompt
        String systemPrompt = loadResource(agentPromptTemplate);
        String userPrompt = buildAgentUserPrompt(question, history, pre.rewrittenQuery());

        log.info("[Agent] 检索开始 question=\"{}\" rewritten=\"{}\"", question, pre.rewrittenQuery());

        // 3. 调用 LLM（带 Function Calling，Spring AI 自动处理 Tool Loop）
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .functions(FunctionCallback.builder()
                            .function("searchKnowledge", searchTool)
                            .inputType(SearchKnowledgeTool.Request.class)
                            .build())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[Agent] 调用失败，降级到标准 RAG 管线: {}", e.getMessage());
            return fallbackToStandardPipeline(question, spaceIds, history, category);
        }

        // 4. 汇总 Agent 多次搜索累积的文档
        List<SearchResult> allSources = searchTool.getCachedResults();

        // 5. 置信度评估
        String confidence = evaluateConfidence(allSources, answer);

        // 6. 构造溯源
        List<ChatResponse.SourceInfo> sourceInfos;
        if ("low".equals(confidence)) {
            sourceInfos = List.of();
        } else {
            sourceInfos = allSources.stream()
                    .map(s -> new ChatResponse.SourceInfo(
                            s.getDocumentTitle(), s.getDocumentId(),
                            s.getChunkIndex(), s.getContent()))
                    .toList();
        }

        ChatResponse response = new ChatResponse(answer, sourceInfos,
                allSources.isEmpty(), confidence);

        // 7. 写入搜索日志
        int total = (int) (System.currentTimeMillis() - tStart);
        saveSearchLog(question, answer, allSources.size(), confidence,
                false, spaceIds, category, total, collectSourceTitles(allSources));

        log.info("[Agent] 检索完成 total={}ms searchCalls={} uniqueChunks={} confidence={} question=\"{}\"",
                total, searchTool.getCallCount(), allSources.size(), confidence, question);

        return response;
    }

    /**
     * Agent 流式模式：LLM 多步检索（非流式 Function Calling）+ Flux 包装结果。
     *
     * <p>与 {@link #agentChat} 的区别：返回 {@link StreamContext} 而非 {@link ChatResponse}，
     * 浏览器端仍能收到 SSE 事件（只是无打字动画——整个答案作为一个 token 推送）。
     *
     * <p>返回 null 表示 Agent 调用失败，调用方应降级到标准流式管线。
     */
    private StreamContext agentChatStream(String question, List<Long> spaceIds,
                                          List<Map<String, String>> history,
                                          String category, QueryPreprocessor.Result pre,
                                          long startMs, Timer.Sample requestSample) {
        // 1. 创建本次请求的检索工具
        var searchTool = new SearchKnowledgeTool(
                hybridSearchService, spaceIds, category);

        // 2. 加载 Prompt
        String systemPrompt = loadResource(agentPromptTemplate);
        String userPrompt = buildAgentUserPrompt(question, history, pre.rewrittenQuery());

        log.info("[AgentStream] 检索开始 question=\"{}\" rewritten=\"{}\"", question, pre.rewrittenQuery());

        // 3. Agent 多步检索（非流式，含 Function Calling 工具循环）
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .functions(FunctionCallback.builder()
                            .function("searchKnowledge", searchTool)
                            .inputType(SearchKnowledgeTool.Request.class)
                            .build())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[AgentStream] 调用失败，降级到标准流式管线: {}", e.getMessage());
            return null; // 通知调用方降级
        }

        // 4. 汇总搜索结果 + 评估置信度
        List<SearchResult> allSources = searchTool.getCachedResults();
        String confidence = evaluateConfidence(allSources, answer);
        List<SearchResult> effectiveSources = "low".equals(confidence) ? List.of() : allSources;

        // 5. 兜底处理
        boolean fallback = allSources.isEmpty() || answer == null || answer.isBlank();
        String finalAnswer = (!fallback) ? answer
                : "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。";

        // 6. 记录指标和日志
        metrics.recordSuccess();
        requestSample.stop(metrics.requestTimer());
        int total = (int) (System.currentTimeMillis() - startMs);
        saveSearchLog(question, answer, allSources.size(), confidence,
                false, spaceIds, category, total, collectSourceTitles(allSources));

        log.info("[AgentStream] 检索完成 total={}ms searchCalls={} uniqueChunks={} confidence={} fallback={} question=\"{}\"",
                total, searchTool.getCallCount(), allSources.size(), confidence, fallback, question);

        // 7. 返回 StreamContext — 整个答案作为单个 Flux 元素
        return new StreamContext(
                Flux.just(finalAnswer),
                effectiveSources, fallback, confidence);
    }

    /**
     * Agent 异常时降级到标准 RAG 管线（一次检索 + LLM 生成）。
     */
    private ChatResponse fallbackToStandardPipeline(String question, List<Long> spaceIds,
                                                    List<Map<String, String>> history,
                                                    String category) {
        long tStart = System.currentTimeMillis();

        QueryPreprocessor.Result pre = preprocessor.preprocess(question);
        if (pre.isFaqMatched()) {
            var src = new ChatResponse.SourceInfo(
                    "📋 知识库预设问答", null, null,
                    pre.faqQuestion() + " — 该回答来自知识库预设的高频问答");
            ChatResponse r = new ChatResponse(pre.faqAnswer(), List.of(src), false, "high");
            int total = (int) (System.currentTimeMillis() - tStart);
            saveSearchLog(question, pre.faqAnswer(), 1, "high", true, spaceIds, category, total, null);
            return r;
        }

        String processed = pre.rewrittenQuery();
        List<SearchResult> sources = hybridSearchService.search(
                processed, adaptiveTopK(question), spaceIds, category);

        if (sources.isEmpty()) {
            ChatResponse r = new ChatResponse(
                    "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。",
                    Collections.emptyList(), true, "low");
            saveSearchLog(question, r.getAnswer(), 0, "low", false, spaceIds, category,
                    (int) (System.currentTimeMillis() - tStart), null);
            return r;
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SearchResult s = sources.get(i);
            context.append("【参考资料").append(i + 1).append("】\n")
                    .append(s.getContent()).append("\n\n");
        }

        String prompt = buildPrompt(context.toString(), question, history);
        String answer;
        try {
            answer = chatClient.prompt().user(prompt)
                    .stream().content()
                    .collectList()
                    .map(list -> String.join("", list))
                    .block(Duration.ofSeconds(120));
            if (answer == null || answer.isEmpty()) {
                answer = buildFallbackAnswer(sources);
            }
        } catch (Exception e) {
            log.error("降级管线 LLM 调用也失败了: {}", e.getMessage());
            answer = buildFallbackAnswer(sources);
        }

        String confidence = evaluateConfidence(sources, answer);
        List<ChatResponse.SourceInfo> sourceInfos = "low".equals(confidence) ? List.of()
                : sources.stream()
                    .map(s -> new ChatResponse.SourceInfo(
                            s.getDocumentTitle(), s.getDocumentId(),
                            s.getChunkIndex(), s.getContent()))
                    .toList();

        ChatResponse response = new ChatResponse(answer, sourceInfos,
                sources.isEmpty(), confidence);
        saveSearchLog(question, answer, sources.size(), confidence,
                false, spaceIds, category, (int) (System.currentTimeMillis() - tStart),
                collectSourceTitles(sources));
        return response;
    }

    /** 从 classpath 加载文本资源 */
    private String loadResource(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("资源加载失败: {}", resource.getDescription());
            return "";
        }
    }

    /** 拼接 Agent 模式的用户 Prompt */
    private String buildAgentUserPrompt(String question, List<Map<String, String>> history,
                                        String rewrittenQuery) {
        StringBuilder sb = new StringBuilder();
        String historyText = formatHistory(history);
        if (!historyText.isEmpty()) {
            sb.append(historyText).append("\n");
        }
        sb.append("用户问题：").append(question);
        // 如果 QueryPreprocessor 改写了问题，附上改写版本帮助 LLM 理解
        if (rewrittenQuery != null && !rewrittenQuery.equals(question)) {
            sb.append("\n（问题改写：").append(rewrittenQuery).append("）");
        }
        return sb.toString();
    }

}
