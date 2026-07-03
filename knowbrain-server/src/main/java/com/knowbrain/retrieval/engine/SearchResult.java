package com.knowbrain.retrieval.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索结果 — 向量搜索返回的文档片段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /** 切片文本内容 */
    private String content;

    /** 所属文档标题 */
    private String documentTitle;

    /** 文档 ID */
    private Long documentId;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 相似度得分 */
    private Double score;
}
