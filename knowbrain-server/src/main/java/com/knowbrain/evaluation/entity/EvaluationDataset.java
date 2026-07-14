package com.knowbrain.evaluation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评测数据集 — kb_evaluation_dataset
 *
 * <p>管理员创建测试数据集，包含多条评测问题。
 * 评测运行时选中一个数据集，逐题执行 RAG + LLM-as-Judge 评分。
 */
@Data
@TableName("kb_evaluation_dataset")
public class EvaluationDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据集名称 */
    private String name;

    /** 描述说明 */
    private String description;

    /** 关联场景: it-helpdesk / hr-policy / general */
    private String scenario;

    /** 问题数量（冗余计数，列表展示用） */
    private Integer questionCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
