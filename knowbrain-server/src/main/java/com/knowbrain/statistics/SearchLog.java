package com.knowbrain.statistics;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搜索日志 — kb_search_log
 */
@Data
@TableName("kb_search_log")
public class SearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String question;

    /** AI 回答（截取前 500 字） */
    private String answer;

    /** 引用来源数 */
    private Integer sourcesCount;

    /** 引用文档标题（逗号分隔） */
    private String sourceTitles;

    /** 置信度: high / medium / low */
    private String confidence;

    /** 是否 FAQ 命中 */
    private Integer faqMatched;

    private Long userId;

    /** 检索空间范围（逗号分隔） */
    private String spaceIds;

    /** 分类过滤 */
    private String category;

    /** 问答总耗时（毫秒） */
    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
