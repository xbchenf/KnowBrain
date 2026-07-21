package com.knowbrain.retrieval.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG 问答响应
 */
@Data
@NoArgsConstructor
public class ChatResponse {

    /** LLM 生成的答案 */
    private String answer;

    /** 溯源引用列表 */
    private List<SourceInfo> sources;

    /** 是否触发兜底（检索无结果或 LLM 超时） */
    private boolean fallback;

    /** 置信度：high / medium / low */
    private String confidence;

    /** 思考链事件（仅 Agent 模式下非空），用于前端可视化 */
    private List<Map<String, Object>> thinkingEvents;

    /** 完整构造函数（非 Agent 路径，thinkingEvents 默认为 null） */
    public ChatResponse(String answer, List<SourceInfo> sources, boolean fallback, String confidence) {
        this.answer = answer;
        this.sources = sources;
        this.fallback = fallback;
        this.confidence = confidence;
    }

    public ChatResponse(String answer, List<SourceInfo> sources, boolean fallback, String confidence,
                        List<Map<String, Object>> thinkingEvents) {
        this.answer = answer;
        this.sources = sources;
        this.fallback = fallback;
        this.confidence = confidence;
        this.thinkingEvents = thinkingEvents;
    }

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
