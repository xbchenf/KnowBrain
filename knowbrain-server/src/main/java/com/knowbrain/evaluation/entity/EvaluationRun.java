package com.knowbrain.evaluation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评测运行记录 — kb_evaluation_run
 *
 * <p>每次评测运行对应一条记录，记录整体状态和汇总指标。
 * 运行完成后 avg_* 字段填充三项指标的均值。
 */
@Data
@TableName("kb_evaluation_run")
public class EvaluationRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所用数据集 ID */
    private Long datasetId;

    /** 状态: RUNNING / COMPLETED / FAILED */
    private String status;

    /** 总问题数 */
    private Integer totalQuestions;

    /** 已完成问题数 */
    private Integer completedQuestions;

    /** 平均忠实度 0.000-1.000 */
    private java.math.BigDecimal avgFaithfulness;

    /** 平均答案相关性 0.000-1.000 */
    private java.math.BigDecimal avgRelevance;

    /** 平均上下文召回率 0.000-1.000 */
    private java.math.BigDecimal avgContextRecall;

    /** 平均延迟（毫秒） */
    private Integer avgLatencyMs;

    /** 评测开始时间 */
    private LocalDateTime startedAt;

    /** 评测完成时间 */
    private LocalDateTime completedAt;

    /** 失败原因（status=FAILED 时填充） */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
