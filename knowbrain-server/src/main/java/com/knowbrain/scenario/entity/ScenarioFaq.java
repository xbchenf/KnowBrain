package com.knowbrain.scenario.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 高频问答 — kb_scenario_faq
 */
@Data
@TableName("kb_scenario_faq")
public class ScenarioFaq {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 匹配关键词（逗号分隔） */
    private String keywords;

    /** 标准问题 */
    private String question;

    /** 预设答案 */
    private String answer;

    /** 所属分类 key */
    private String category;

    /** 启用状态 */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
