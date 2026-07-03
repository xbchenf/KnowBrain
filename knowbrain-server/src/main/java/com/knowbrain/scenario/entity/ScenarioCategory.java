package com.knowbrain.scenario.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识分类 — kb_scenario_category
 */
@Data
@TableName("kb_scenario_category")
public class ScenarioCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类唯一标识 */
    @TableField("`key`")
    private String key;

    /** 父分类 key（一级分类为 null） */
    private String parentKey;

    /** 排序序号 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
