package com.knowbrain.evaluation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 单题评测结果 — kb_evaluation_result
 *
 * <p>每题一行，记录 RAG 实际输出 + LLM-as-Judge 三项指标评分。
 * retrieved_chunks 和 llm_eval_raw 以 JSON 文本存储，便于调试。
 */
@Data
@TableName("kb_evaluation_result")
public class EvaluationResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属运行 ID */
    private Long runId;

    /** 对应问题 ID */
    private Long questionId;

    /** 问题文本（冗余存储，避免 JOIN 查问题表） */
    private String questionText;

    /** RAG 实际返回的答案 */
    private String actualAnswer;

    /** 检索到的文档片段（JSON 数组: [{title, documentId, chunkIndex, text}]） */
    private String retrievedChunks;

    /** 忠实度 0.000-1.000 */
    private java.math.BigDecimal faithfulness;

    /** 答案相关性 0.000-1.000 */
    private java.math.BigDecimal answerRelevance;

    /** 上下文召回率 0.000-1.000 */
    private java.math.BigDecimal contextRecall;

    /** 本次查询延迟（毫秒） */
    private Integer latencyMs;

    /** LLM 评测原始输出（JSON，调试用 — 包含三项指标的原始 reason） */
    private String llmEvalRaw;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
