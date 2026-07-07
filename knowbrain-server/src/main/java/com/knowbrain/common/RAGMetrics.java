package com.knowbrain.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * RAG 业务指标 — 通过 Micrometer 暴露给 Prometheus
 */
@Component
public class RAGMetrics {

    private final MeterRegistry registry;

    // ── Counters ──
    private final Counter requestsSuccess;
    private final Counter requestsFallback;
    private final Counter requestsEmpty;
    private final Counter requestsFaq;
    private final Counter cacheHits;
    private final Counter faqHits;
    private final Counter documentsUploaded;
    private final Counter loginFailures;

    // ── Timers ──
    private final Timer requestDuration;
    private final Timer searchDuration;
    private final Timer llmDuration;

    public RAGMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.requestsSuccess = Counter.builder("rag.requests.total")
                .tag("result", "success")
                .description("RAG 问答成功次数")
                .register(registry);
        this.requestsFallback = Counter.builder("rag.requests.total")
                .tag("result", "fallback")
                .description("RAG 问答降级次数（LLM 失败）")
                .register(registry);
        this.requestsEmpty = Counter.builder("rag.requests.total")
                .tag("result", "empty")
                .description("RAG 检索无结果次数")
                .register(registry);
        this.requestsFaq = Counter.builder("rag.requests.total")
                .tag("result", "faq")
                .description("FAQ 命中次数")
                .register(registry);

        this.cacheHits = Counter.builder("rag.cache.hits")
                .description("Redis 缓存命中次数")
                .register(registry);

        this.faqHits = Counter.builder("rag.faq.hits")
                .description("FAQ 精确匹配命中次数")
                .register(registry);

        this.documentsUploaded = Counter.builder("rag.documents.uploaded")
                .description("文档上传次数")
                .register(registry);

        this.loginFailures = Counter.builder("http.login.failures")
                .description("登录失败次数")
                .register(registry);

        this.requestDuration = Timer.builder("rag.request.duration")
                .description("RAG 问答全链路耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.searchDuration = Timer.builder("rag.search.duration")
                .description("混合检索耗时")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
        this.llmDuration = Timer.builder("rag.llm.duration")
                .description("LLM 调用耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // ── Counter accessors ──

    public void recordSuccess()           { requestsSuccess.increment(); }
    public void recordFallback()          { requestsFallback.increment(); }
    public void recordEmpty()             { requestsEmpty.increment(); }
    public void recordFaqHit()            { requestsFaq.increment(); }
    public void recordCacheHit()          { cacheHits.increment(); }
    public void recordDocumentUploaded()  { documentsUploaded.increment(); }
    public void recordLoginFailure()      { loginFailures.increment(); }

    // ── Timer accessors ──

    public Timer requestTimer() { return requestDuration; }
    public Timer searchTimer()  { return searchDuration; }
    public Timer llmTimer()     { return llmDuration; }
}
