package com.knowbrain.retrieval.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    @Value("classpath:prompts/rag-system.txt")
    private Resource promptTemplate;

    public RAGServiceImpl(HybridSearchService hybridSearchService,
                          ChatClient.Builder chatClientBuilder,
                          QueryPreprocessor preprocessor,
                          RAGCacheService cacheService) {
        this.hybridSearchService = hybridSearchService;
        this.chatClient = chatClientBuilder.build();
        this.preprocessor = preprocessor;
        this.cacheService = cacheService;
    }

    @Override
    public ChatResponse chat(String question) {
        return chat(question, Collections.emptyList());
    }

    @Override
    public ChatResponse chat(String question, List<Long> spaceIds) {
        // 0. Redis 缓存检查（高频问题短路）
        ChatResponse cached = cacheService.get(question, spaceIds);
        if (cached != null) {
            return cached;
        }

        // 1. 预处理：术语改写 + FAQ 精确匹配
        QueryPreprocessor.Result pre = preprocessor.preprocess(question);

        // 2. FAQ 命中 → 直接返回预设答案（短路，不缓存）
        if (pre.isFaqMatched()) {
            return new ChatResponse(pre.faqAnswer(), List.of(), false, "high");
        }

        String processed = pre.rewrittenQuery();

        // 3. 混合检索 Top-5 相关切片（带空间过滤）
        List<SearchResult> sources = search(processed, 5, spaceIds);

        // 4. 无结果 → 兜底（不缓存）
        if (sources.isEmpty()) {
            return new ChatResponse(
                    "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。",
                    Collections.emptyList(), true, "low");
        }

        // 5. 拼接上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SearchResult s = sources.get(i);
            context.append("【参考资料").append(i + 1).append("】\n")
                    .append(s.getContent()).append("\n\n");
        }

        // 6. 加载 Prompt 模板 + 调用 LLM
        String prompt = buildPrompt(context.toString(), question);
        String answer;
        try {
            answer = chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM 调用失败，降级返回检索原文: {}", e.getMessage());
            String fallback = buildFallbackAnswer(sources);
            List<ChatResponse.SourceInfo> fallbackSources = sources.stream()
                    .map(s -> new ChatResponse.SourceInfo(
                            s.getDocumentTitle(), s.getDocumentId(),
                            s.getChunkIndex(), s.getContent()))
                    .toList();
            return new ChatResponse(fallback, fallbackSources, true, "low");
        }

        // 7. 构造溯源 + 置信度
        List<ChatResponse.SourceInfo> sourceInfos = sources.stream()
                .map(s -> new ChatResponse.SourceInfo(
                        s.getDocumentTitle(), s.getDocumentId(),
                        s.getChunkIndex(), s.getContent()))
                .toList();

        String confidence = evaluateConfidence(sources, answer);

        ChatResponse response = new ChatResponse(answer, sourceInfos, false, confidence);

        // 8. 写入 Redis 缓存（仅 medium+ 非降级回答）
        cacheService.put(question, spaceIds, response);

        return response;
    }

    @Override
    public StreamContext chatStream(String question, List<Long> spaceIds) {
        // 0. Redis 缓存检查（高频问题短路）
        ChatResponse cached = cacheService.get(question, spaceIds);
        if (cached != null) {
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

        // 1. 预处理
        QueryPreprocessor.Result pre = preprocessor.preprocess(question);

        // 2. FAQ 命中 → 单元素 Flux（短路返回预设答案）
        if (pre.isFaqMatched()) {
            return new StreamContext(
                    Flux.just(pre.faqAnswer()),
                    List.of(), false, "high");
        }

        String processed = pre.rewrittenQuery();

        // 3. 混合检索
        List<SearchResult> sources = search(processed, 5, spaceIds);

        // 4. 无结果 → 兜底
        if (sources.isEmpty()) {
            String fallbackMsg = "未找到与您问题相关的文档，请尝试换个问法或补充更多文档。";
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

        String prompt = buildPrompt(context.toString(), question);

        // 6. LLM 流式生成
        Flux<String> tokenFlux = chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .onErrorResume(e -> {
                    log.error("LLM 流式调用失败，降级返回检索原文: {}", e.getMessage());
                    String fb = buildFallbackAnswer(sources);
                    return Flux.just(fb);
                });

        String confidence = evaluateConfidence(sources, null);

        return new StreamContext(tokenFlux, sources, false, confidence);
    }

    @Override
    public List<SearchResult> search(String question, int topK) {
        return search(question, topK, Collections.emptyList());
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1000))
    @Override
    public List<SearchResult> search(String question, int topK, List<Long> spaceIds) {
        return hybridSearchService.search(question, topK, spaceIds);
    }

    // ==================== 辅助方法 ====================

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

    private String buildPrompt(String context, String question) {
        try {
            String template = new String(promptTemplate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return template.replace("{context}", context).replace("{question}", question);
        } catch (IOException e) {
            log.warn("Prompt 模板加载失败，使用默认模板", e);
            return """
                    你是一个企业智能知识库助手。请根据以下参考资料回答用户问题。
                    要求：
                    1. 仅基于参考资料回答，不要编造信息
                    2. 如果参考资料不足以回答，请明确说明"当前文档库中未找到相关信息"
                    3. 回答简洁、清晰、专业
                    4. 引用具体文档时，使用 [来源: 文档名] 标注

                    参考资料：
                    %s

                    用户问题：%s
                    """.formatted(context, question);
        }
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

        if (sources.size() >= 3 && maxScore > 0.8) return "high";
        if (sources.size() >= 2 && maxScore > 0.6) return "medium";
        return "low";
    }

}
