package com.knowbrain.retrieval.engine;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式问答上下文 — 包含 token 流 + 检索结果
 *
 * 用于 SSE 流式输出场景：Controller 订阅 token 流逐字推送，
 * 流结束后使用 sources / confidence 发送最终元数据。
 */
public record StreamContext(
        /** LLM 生成的 token 流（如 FAQ/降级 命中则单元素） */
        Flux<String> tokens,
        /** 检索结果溯源列表 */
        List<SearchResult> sources,
        /** 是否触发兜底 */
        boolean fallback,
        /** 置信度 */
        String confidence
) {}
