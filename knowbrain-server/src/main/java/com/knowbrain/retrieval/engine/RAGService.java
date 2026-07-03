package com.knowbrain.retrieval.engine;

import java.util.List;

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
     * RAG 问答（不过滤空间）
     */
    ChatResponse chat(String question);

    /**
     * RAG 问答（带空间权限过滤）
     */
    ChatResponse chat(String question, List<Long> spaceIds);

    /**
     * 流式 RAG 问答（SSE）— 返回 token 流 + 检索上下文
     *
     * @param question 用户问题
     * @param spaceIds 可访问空间 ID 列表
     * @return StreamContext 包含 Flux<String> tokens + 检索 sources
     */
    StreamContext chatStream(String question, List<Long> spaceIds);
}
