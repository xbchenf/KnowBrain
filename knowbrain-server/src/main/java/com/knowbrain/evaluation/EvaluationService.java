package com.knowbrain.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowbrain.evaluation.config.EvaluationJudgeConfig;
import com.knowbrain.evaluation.entity.*;
import com.knowbrain.evaluation.mapper.*;
import com.knowbrain.retrieval.engine.ChatResponse;
import com.knowbrain.retrieval.engine.RAGService;
import com.knowbrain.retrieval.engine.RAGCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 评测服务 — 数据集管理 + 评测运行 + LLM-as-Judge 评分
 *
 * <p>核心流程：选中数据集 → 逐题调用 RAG 管线 → LLM 三项评分 → 汇总结果
 */
@Slf4j
@Service
public class EvaluationService {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^}]+\\}");

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationQuestionMapper questionMapper;
    private final EvaluationRunMapper runMapper;
    private final EvaluationResultMapper resultMapper;
    private final RAGService ragService;
    private final RAGCacheService cacheService;
    private final ChatClient judgeClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    @Value("classpath:prompts/eval-faithfulness.txt")
    private Resource faithfulnessTemplate;

    @Value("classpath:prompts/eval-relevance.txt")
    private Resource relevanceTemplate;

    @Value("classpath:prompts/eval-context-recall.txt")
    private Resource contextRecallTemplate;

    public EvaluationService(EvaluationDatasetMapper datasetMapper,
                             EvaluationQuestionMapper questionMapper,
                             EvaluationRunMapper runMapper,
                             EvaluationResultMapper resultMapper,
                             RAGService ragService,
                             RAGCacheService cacheService,
                             ChatClient.Builder primaryBuilder,
                             EvaluationJudgeConfig judgeConfig,
                             ObjectMapper objectMapper,
                             TaskExecutor taskExecutor) {
        this.datasetMapper = datasetMapper;
        this.questionMapper = questionMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.ragService = ragService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;

        // 配置了独立 Judge → 创建独立 ChatClient（temperature=0.0 确定性输出）
        // 未配置 → 复用主 ChatClient（同模型评测）
        if (judgeConfig.isConfigured()) {
            this.judgeClient = createJudgeClient(judgeConfig);
            log.info("评测 Judge 模型: {} @ {} (独立客户端)", judgeConfig.getModel(), judgeConfig.getBaseUrl());
        } else {
            this.judgeClient = primaryBuilder.build();
            log.info("评测 Judge 模型: 复用主 ChatClient (同模型评测)");
        }
    }

    // ==================== 数据集管理 ====================

    public IPage<EvaluationDataset> listDatasets(String scenario, int page, int size) {
        LambdaQueryWrapper<EvaluationDataset> qw = new LambdaQueryWrapper<>();
        if (scenario != null && !scenario.isBlank()) {
            qw.eq(EvaluationDataset::getScenario, scenario);
        }
        qw.orderByDesc(EvaluationDataset::getCreateTime);
        return datasetMapper.selectPage(new Page<>(page, size), qw);
    }

    @Transactional
    public EvaluationDataset createDataset(EvaluationDataset dataset) {
        dataset.setQuestionCount(0);
        datasetMapper.insert(dataset);
        return dataset;
    }

    @Transactional
    public void deleteDataset(Long id) {
        // 级联删除：问题 → 运行结果 → 运行记录
        LambdaQueryWrapper<EvaluationQuestion> qwQ = new LambdaQueryWrapper<>();
        qwQ.eq(EvaluationQuestion::getDatasetId, id);
        List<EvaluationQuestion> questions = questionMapper.selectList(qwQ);
        for (EvaluationQuestion q : questions) {
            deleteQuestionResults(q.getId());
        }
        questionMapper.delete(qwQ);

        LambdaQueryWrapper<EvaluationRun> qwR = new LambdaQueryWrapper<>();
        qwR.eq(EvaluationRun::getDatasetId, id);
        List<EvaluationRun> runs = runMapper.selectList(qwR);
        for (EvaluationRun run : runs) {
            LambdaQueryWrapper<EvaluationResult> qwRes = new LambdaQueryWrapper<>();
            qwRes.eq(EvaluationResult::getRunId, run.getId());
            resultMapper.delete(qwRes);
        }
        runMapper.delete(qwR);

        datasetMapper.deleteById(id);
    }

    // ==================== 问题管理 ====================

    public IPage<EvaluationQuestion> listQuestions(Long datasetId, int page, int size) {
        LambdaQueryWrapper<EvaluationQuestion> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationQuestion::getDatasetId, datasetId);
        qw.orderByAsc(EvaluationQuestion::getId);
        return questionMapper.selectPage(new Page<>(page, size), qw);
    }

    @Transactional
    public EvaluationQuestion addQuestion(EvaluationQuestion question) {
        questionMapper.insert(question);
        updateQuestionCount(question.getDatasetId());
        return question;
    }

    @Transactional
    public void updateQuestion(Long id, EvaluationQuestion question) {
        question.setId(id);
        questionMapper.updateById(question);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        EvaluationQuestion q = questionMapper.selectById(id);
        if (q != null) {
            deleteQuestionResults(id);
            questionMapper.deleteById(id);
            updateQuestionCount(q.getDatasetId());
        }
    }

    @Transactional
    public int importQuestions(Long datasetId, List<EvaluationQuestion> questions) {
        int count = 0;
        for (EvaluationQuestion q : questions) {
            q.setDatasetId(datasetId);
            questionMapper.insert(q);
            count++;
        }
        updateQuestionCount(datasetId);
        return count;
    }

    // ==================== 评测运行 ====================

    @Transactional
    public EvaluationRun startRun(Long datasetId) {
        List<EvaluationQuestion> questions = listAllQuestions(datasetId);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("数据集没有评测问题，请先添加问题");
        }

        EvaluationRun run = new EvaluationRun();
        run.setDatasetId(datasetId);
        run.setStatus("RUNNING");
        run.setTotalQuestions(questions.size());
        run.setCompletedQuestions(0);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        // 异步执行评测（TaskExecutor 线程池，不阻塞 HTTP 响应）
        taskExecutor.execute(() -> executeRun(run, questions));

        return run;
    }

    public IPage<EvaluationRun> listRuns(int page, int size) {
        LambdaQueryWrapper<EvaluationRun> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(EvaluationRun::getCreateTime);
        return runMapper.selectPage(new Page<>(page, size), qw);
    }

    public EvaluationRun getRun(Long id) {
        return runMapper.selectById(id);
    }

    public IPage<EvaluationResult> getRunResults(Long runId, int page, int size) {
        LambdaQueryWrapper<EvaluationResult> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationResult::getRunId, runId);
        qw.orderByAsc(EvaluationResult::getId);
        return resultMapper.selectPage(new Page<>(page, size), qw);
    }

    // ==================== 内部：评测流水线 ====================

    void executeRun(EvaluationRun run, List<EvaluationQuestion> questions) {
        log.info("评测开始: runId={} datasetId={} totalQuestions={}", run.getId(), run.getDatasetId(), questions.size());
        try {
            for (int i = 0; i < questions.size(); i++) {
                EvaluationQuestion q = questions.get(i);
                try {
                    // 清空缓存确保实时评测
                    cacheService.invalidateAll();

                    EvaluationResult result = evaluateSingle(q);
                    result.setRunId(run.getId());
                    result.setQuestionId(q.getId());
                    resultMapper.insert(result);

                    run.setCompletedQuestions(i + 1);
                    runMapper.updateById(run);
                } catch (Exception e) {
                    log.warn("评测单题失败: questionId={}", q.getId(), e);
                    // 记录失败题（不暴露内部异常详情到持久化结果中）
                    EvaluationResult failResult = new EvaluationResult();
                    failResult.setRunId(run.getId());
                    failResult.setQuestionId(q.getId());
                    failResult.setQuestionText(q.getQuestion());
                    failResult.setActualAnswer("评测失败，请查看服务端日志");
                    failResult.setFaithfulness(BigDecimal.ZERO);
                    failResult.setAnswerRelevance(BigDecimal.ZERO);
                    failResult.setContextRecall(BigDecimal.ZERO);
                    failResult.setLatencyMs(0);
                    failResult.setLlmEvalRaw("{\"error\": \"internal error\"}");
                    resultMapper.insert(failResult);
                    run.setCompletedQuestions(i + 1);
                    runMapper.updateById(run);
                }
            }

            // 计算汇总指标
            computeAggregates(run.getId());
            run.setStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            runMapper.updateById(run);
            log.info("评测完成: runId={} questions={}", run.getId(), questions.size());
        } catch (Exception e) {
            log.error("评测运行失败: runId={}", run.getId(), e);
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runMapper.updateById(run);
        }
    }

    EvaluationResult evaluateSingle(EvaluationQuestion q) {
        long startMs = System.currentTimeMillis();

        // 1. 调用 RAG 管线（skipFaq=true 强制走完整检索+LLM，避免 FAQ 短路干扰评测）
        ChatResponse cr = ragService.chat(q.getQuestion(),
                List.of(), List.of(), null, true);
        long latencyMs = System.currentTimeMillis() - startMs;

        // 2. 构建检索上下文文本
        String context = buildContextText(cr);

        // 3. 序列化检索片段（供存储）
        String retrievedChunksJson = serializeRetrievedChunks(cr);

        // 4. 并行调用 LLM-as-Judge 三项评分
        CompletableFuture<JudgeResult> fFaith = CompletableFuture.supplyAsync(() ->
                judge("faithfulness", faithfulnessTemplate, q.getQuestion(), cr.getAnswer(), context));
        CompletableFuture<JudgeResult> fRelevance = CompletableFuture.supplyAsync(() ->
                judge("relevance", relevanceTemplate, q.getQuestion(), cr.getAnswer(), context));
        CompletableFuture<JudgeResult> fRecall = CompletableFuture.supplyAsync(() ->
                judge("context_recall", contextRecallTemplate, q.getQuestion(), cr.getAnswer(), context));

        JudgeResult faith = fFaith.join();
        JudgeResult relevance = fRelevance.join();
        JudgeResult recall = fRecall.join();

        // 5. 构建 LLM 原始评测 JSON
        String llmEvalRaw = buildEvalRawJson(faith, relevance, recall);

        // 6. 组装结果
        EvaluationResult result = new EvaluationResult();
        result.setQuestionText(q.getQuestion());
        result.setActualAnswer(cr.getAnswer());
        result.setRetrievedChunks(retrievedChunksJson);
        result.setFaithfulness(toDecimal(faith.score));
        result.setAnswerRelevance(toDecimal(relevance.score));
        result.setContextRecall(toDecimal(recall.score));
        result.setLatencyMs((int) latencyMs);
        result.setLlmEvalRaw(llmEvalRaw);
        return result;
    }

    // ==================== 内部：LLM-as-Judge ====================

    private JudgeResult judge(String metricName, Resource template, String question, String answer, String context) {
        try {
            String prompt = loadTemplate(template)
                    .replace("{question}", question)
                    .replace("{answer}", answer != null ? answer : "")
                    .replace("{context}", context);

            String response = judgeClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.warn("Judge {} 返回空响应", metricName);
                return new JudgeResult(0.0, "LLM 返回空响应");
            }

            return parseJudgeResponse(response);
        } catch (Exception e) {
            log.warn("Judge {} 调用失败: {}", metricName, e.getMessage());
            return new JudgeResult(0.0, "调用异常: " + e.getMessage());
        }
    }

    private String loadTemplate(Resource template) throws IOException {
        return new String(template.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private JudgeResult parseJudgeResponse(String response) {
        Matcher m = JSON_PATTERN.matcher(response);
        if (m.find()) {
            String json = m.group();
            try {
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                double score = map.containsKey("score")
                        ? ((Number) map.get("score")).doubleValue()
                        : 0.0;
                String reason = map.containsKey("reason")
                        ? (String) map.get("reason")
                        : "无理由";
                // 钳制 score 到 [0, 1]
                score = Math.max(0.0, Math.min(1.0, score));
                return new JudgeResult(score, reason);
            } catch (JsonProcessingException e) {
                log.warn("Judge JSON 解析失败: {}", json);
            }
        }
        // fallback: 尝试从整个响应中提取数字
        try {
            double score = Double.parseDouble(response.trim());
            return new JudgeResult(Math.max(0.0, Math.min(1.0, score)), "extracted from raw number");
        } catch (NumberFormatException ignored) {
        }
        return new JudgeResult(0.0, "解析失败: " + response.substring(0, Math.min(100, response.length())));
    }

    // ==================== 内部：辅助方法 ====================

    /**
     * 创建独立 Judge ChatClient（temperature=0.0 确保确定性输出）
     */
    private ChatClient createJudgeClient(EvaluationJudgeConfig config) {
        OpenAiApi api = new OpenAiApi(config.getBaseUrl(), config.getApiKey());
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(config.getModel())
                .withTemperature(0.0)
                .build();
        OpenAiChatModel chatModel = new OpenAiChatModel(api, options);
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 从 ChatResponse 构建检索上下文文本
     */
    private String buildContextText(ChatResponse cr) {
        if (cr.getSources() == null || cr.getSources().isEmpty()) {
            return "(无检索结果)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cr.getSources().size(); i++) {
            ChatResponse.SourceInfo s = cr.getSources().get(i);
            sb.append("【参考资料").append(i + 1).append("】")
                    .append(s.getTitle() != null ? " " + s.getTitle() : "")
                    .append("\n")
                    .append(s.getText())
                    .append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 序列化检索片段为 JSON
     */
    private String serializeRetrievedChunks(ChatResponse cr) {
        if (cr.getSources() == null || cr.getSources().isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> chunks = cr.getSources().stream()
                    .map(s -> Map.<String, Object>of(
                            "title", s.getTitle() != null ? s.getTitle() : "",
                            "documentId", s.getDocumentId() != null ? s.getDocumentId() : 0,
                            "chunkIndex", s.getChunkIndex() != null ? s.getChunkIndex() : 0,
                            "text", s.getText() != null ? s.getText() : ""
                    ))
                    .toList();
            return objectMapper.writeValueAsString(chunks);
        } catch (JsonProcessingException e) {
            log.warn("检索片段序列化失败", e);
            return "[]";
        }
    }

    private String buildEvalRawJson(JudgeResult faith, JudgeResult relevance, JudgeResult recall) {
        try {
            Map<String, Object> raw = Map.of(
                    "faithfulness", Map.of("score", faith.score, "reason", faith.reason),
                    "answer_relevance", Map.of("score", relevance.score, "reason", relevance.reason),
                    "context_recall", Map.of("score", recall.score, "reason", recall.reason)
            );
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 计算运行汇总指标
     */
    private void computeAggregates(Long runId) {
        LambdaQueryWrapper<EvaluationResult> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationResult::getRunId, runId);
        List<EvaluationResult> results = resultMapper.selectList(qw);
        if (results.isEmpty()) return;

        double sumFaith = 0, sumRelevance = 0, sumRecall = 0;
        long sumLatency = 0;
        int count = 0;
        for (EvaluationResult r : results) {
            if (r.getFaithfulness() != null) sumFaith += r.getFaithfulness().doubleValue();
            if (r.getAnswerRelevance() != null) sumRelevance += r.getAnswerRelevance().doubleValue();
            if (r.getContextRecall() != null) sumRecall += r.getContextRecall().doubleValue();
            if (r.getLatencyMs() != null) sumLatency += r.getLatencyMs();
            count++;
        }
        EvaluationRun run = runMapper.selectById(runId);
        run.setAvgFaithfulness(toDecimal(sumFaith / count));
        run.setAvgRelevance(toDecimal(sumRelevance / count));
        run.setAvgContextRecall(toDecimal(sumRecall / count));
        run.setAvgLatencyMs((int) (sumLatency / count));
        runMapper.updateById(run);
    }

    private List<EvaluationQuestion> listAllQuestions(Long datasetId) {
        LambdaQueryWrapper<EvaluationQuestion> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationQuestion::getDatasetId, datasetId);
        qw.orderByAsc(EvaluationQuestion::getId);
        return questionMapper.selectList(qw);
    }

    private void updateQuestionCount(Long datasetId) {
        List<EvaluationQuestion> questions = listAllQuestions(datasetId);
        EvaluationDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset != null) {
            dataset.setQuestionCount(questions.size());
            datasetMapper.updateById(dataset);
        }
    }

    private void deleteQuestionResults(Long questionId) {
        LambdaQueryWrapper<EvaluationResult> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationResult::getQuestionId, questionId);
        resultMapper.delete(qw);
    }

    // ==================== 内部记录类 ====================

    private record JudgeResult(double score, String reason) {}
}
