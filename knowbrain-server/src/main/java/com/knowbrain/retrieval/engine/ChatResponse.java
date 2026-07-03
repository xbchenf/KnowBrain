package com.knowbrain.retrieval.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 问答响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** LLM 生成的答案 */
    private String answer;

    /** 溯源引用列表 */
    private List<SourceInfo> sources;

    /** 是否触发兜底（检索无结果或 LLM 超时） */
    private boolean fallback;

    /** 置信度：high / medium / low */
    private String confidence;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {

        /** 文档标题 */
        private String title;

        /** 文档 ID */
        private Long documentId;

        /** 切片序号（从 0 开始） */
        private Integer chunkIndex;

        /** 引用的文本片段 */
        private String text;
    }
}
