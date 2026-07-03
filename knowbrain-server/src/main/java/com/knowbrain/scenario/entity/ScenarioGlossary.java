package com.knowbrain.scenario.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 术语词典 — kb_scenario_glossary
 */
@Data
@TableName("kb_scenario_glossary")
public class ScenarioGlossary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 口语化术语 */
    private String term;

    /** 正式技术术语 */
    private String formal;

    /** 同义词列表（逗号分隔） */
    private String synonyms;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
