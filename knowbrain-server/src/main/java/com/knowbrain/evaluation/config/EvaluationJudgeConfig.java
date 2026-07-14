package com.knowbrain.evaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 评测 Judge 模型配置 — 绑定 evaluation.judge.* 配置项
 *
 * <p>不配置时（baseUrl/apiKey/model 均为空），EvaluationService 自动复用主 ChatClient，
 * 即「同模型评测」模式。配置后创建独立的 ChatClient，实现「交叉评测」。
 *
 * <p>三种典型场景：
 * <pre>
 * # 场景 1: 同模型评测（默认）— 不配置任何环境变量
 * # 场景 2: 交叉评测 — 用 DeepSeek 评测 Qwen-Max
 *   EVAL_JUDGE_BASE_URL=https://api.deepseek.com/v1
 *   EVAL_JUDGE_API_KEY=sk-xxx
 *   EVAL_JUDGE_MODEL=deepseek-v4-pro
 * # 场景 3: 同一 API 不同模型
 *   EVAL_JUDGE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
 *   EVAL_JUDGE_API_KEY=sk-xxx
 *   EVAL_JUDGE_MODEL=qwen-max
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "evaluation.judge")
public class EvaluationJudgeConfig {

    /** OpenAI 兼容 API 地址（不配置则复用主 ChatClient） */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名称（如 deepseek-v4-pro / qwen-max / claude-sonnet-5） */
    private String model;

    // ===== getters/setters =====

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 是否配置了独立 Judge 模型（三个字段均非空才算已配置）
     */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
