package com.knowbrain.evaluation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评测问题 — kb_evaluation_question
 *
 * <p>每条记录是一个测试问题，可选附带预期答案和预期命中文档 ID。
 * 评测时调用 RAG 管线获取实际答案，与预期进行对比打分。
 */
@Data
@TableName("kb_evaluation_question")
public class EvaluationQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属数据集 ID */
    private Long datasetId;

    /** 测试问题文本 */
    private String question;

    /** 预期答案（可选，人工标注的参考答案） */
    private String expectedAnswer;

    /** 预期命中文档 ID（可选，逗号分隔，用于召回率验证） */
    private String expectedDocIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
