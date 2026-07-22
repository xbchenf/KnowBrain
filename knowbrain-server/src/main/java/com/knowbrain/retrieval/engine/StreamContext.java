package com.knowbrain.retrieval.engine;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 流式问答上下文 — 包含 token 流 + 检索结果
 *
 * 用于 SSE 流式输出场景：
 * Controller 先订阅 thinkingFlux 推送思考链事件，
 * 再订阅 token 流逐字推送，流结束后通过 sourcesSupplier / fallback / confidence 发送元数据。
 */
public record StreamContext(
        /** LLM 生成的 token 流（如 FAQ/降级 命中则单元素） */
        Flux<String> tokens,
        /** 检索结果溯源 — 标准管线为即时计算，Agent 管线为延迟计算（Supplier） */
        Supplier<List<SearchResult>> sourcesSupplier,
        /** 是否触发兜底 */
        boolean fallback,
        /** 置信度 */
        String confidence,
        /** 思考链事件 Flux（Agent 模式为实时流，非 Agent 模式为空 Flux 或一次性 Flux） */
        Flux<Map<String, Object>> thinkingFlux
) {

    /**
     * 便捷工厂：非 Agent 路径，sources 已确定，thinkingEvents 可选。
     */
    public static StreamContext of(Flux<String> tokens, List<SearchResult> sources,
                                   boolean fallback, String confidence,
                                   List<Map<String, Object>> thinkingEvents) {
        return new StreamContext(
                tokens,
                () -> sources,
                fallback,
                confidence,
                toThinkingFlux(thinkingEvents)
        );
    }

    /**
     * 便捷工厂：Agent 路径，sources 延迟计算，thinkingFlux 由调用方构造。
     */
    public static StreamContext agent(Flux<String> tokens, Supplier<List<SearchResult>> sourcesSupplier,
                                      boolean fallback, String confidence,
                                      Flux<Map<String, Object>> thinkingFlux) {
        return new StreamContext(tokens, sourcesSupplier, fallback, confidence, thinkingFlux);
    }

    /**
     * 获取检索结果（调用 Supplier）
     */
    public List<SearchResult> sources() {
        return sourcesSupplier.get();
    }

    /**
     * 从列表创建思考链 Flux（非 Agent 路径 / 向后兼容）。
     */
    public static Flux<Map<String, Object>> toThinkingFlux(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(events);
    }
}
