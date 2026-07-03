package com.knowbrain.scenario;

import lombok.Data;

import java.util.List;

/**
 * 高频问答条目
 */
@Data
public class FaqEntry {

    /** 匹配关键词 */
    private List<String> keywords;

    /** 标准问题 */
    private String question;

    /** 预设答案 */
    private String answer;

    /** 所属分类 key */
    private String category;
}
