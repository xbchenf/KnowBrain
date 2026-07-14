package com.knowbrain.retrieval.engine;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索引擎 — 混合检索 + LLM 生成 + 溯源
 */
public interface RAGService {

    /**
     * 混合检索 Top-K（不过滤空间）
     */
    List<SearchResult> search(String question, int topK);

    /**
     * 混合检索 Top-K（带空间权限过滤）
     */
    List<SearchResult> search(String question, int topK, List<Long> spaceIds);

    /**
     * RAG 问答（不过滤空间，无历史）
     */
    ChatResponse chat(String question);

    /**
     * RAG 问答（带空间权限过滤，无历史）
     */
    ChatResponse chat(String question, List<Long> spaceIds);

    /**
     * RAG 问答（带空间权限过滤 + 对话历史）
     *
     * @param question 用户问题
     * @param spaceIds 可访问空间 ID 列表
     * @param history  对话历史，每项含 role("user"|"assistant") 和 content
     * @param category 可选分类过滤
     */
    ChatResponse chat(String question, List<Long> spaceIds, List<Map<String, String>> history, String category);

    /**
     * RAG 问答（带空间权限过滤 + 对话历史 + 控制是否跳过 FAQ 短路）
     *
     * @param question 用户问题
     * @param spaceIds 可访问空间 ID 列表
     * @param history  对话历史，每项含 role("user"|"assistant") 和 content
     * @param category 可选分类过滤
     * @param skipFaq  true=跳过 FAQ 精确匹配，强制走完整检索+LLM 流水线（评测用）
     */
    ChatResponse chat(String question, List<Long> spaceIds, List<Map<String, String>> history, String category, boolean skipFaq);

    /**
     * 流式 RAG 问答（SSE）— 返回 token 流 + 检索上下文
     *
     * @param question 用户问题
     * @param spaceIds 可访问空间 ID 列表
     * @return StreamContext 包含 Flux<String> tokens + 检索 sources
     */
    StreamContext chatStream(String question, List<Long> spaceIds);

    /**
     * 流式 RAG 问答（SSE）— 带对话历史
     *
     * @param question 用户问题
     * @param spaceIds 可访问空间 ID 列表
     * @param history  对话历史，每项含 role("user"|"assistant") 和 content
     * @param category 可选分类过滤
     */
    StreamContext chatStream(String question, List<Long> spaceIds, List<Map<String, String>> history, String category);
}
