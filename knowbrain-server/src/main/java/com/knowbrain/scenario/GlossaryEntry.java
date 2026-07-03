package com.knowbrain.scenario;

import lombok.Data;

import java.util.List;

/**
 * 术语词典条目
 */
@Data
public class GlossaryEntry {

    /** 口语化术语 */
    private String term;

    /** 正式技术术语 */
    private String formal;

    /** 同义词列表 */
    private List<String> synonyms;
}
