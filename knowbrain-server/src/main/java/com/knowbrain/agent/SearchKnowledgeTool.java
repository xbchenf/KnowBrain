package com.knowbrain.agent;

import com.knowbrain.retrieval.engine.HybridSearchService;
import com.knowbrain.retrieval.engine.SearchResult;

import java.util.*;
import java.util.function.Function;

/**
 * 检索智能体的唯一工具 — 包装 {@link HybridSearchService}，让 LLM 自主多步检索。
 *
 * <p><b>为什么 per-request new 而不是 @Bean</b>：工具需要携带当前请求的
 * spaceIds 和 category（权限过滤），这些是请求级数据，Spring 单例无法携带。
 *
 * <p><b>结果缓存</b>：Agent 可能多次调用 searchKnowledge（针对不同子问题），
 * 每次结果都累积到 {@link #resultCache} 中，最终由 {@link #getCachedResults()}
 * 汇总为 ChatResponse 的 sources。
 */
public class SearchKnowledgeTool implements
        Function<SearchKnowledgeTool.Request, SearchKnowledgeTool.Response> {

    // ==================== 输入 / 输出记录 ====================

    public record Request(String query) {
        public Request {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query 不能为空");
            }
            if (query.length() > 500) {
                throw new IllegalArgumentException("query 过长（最多 500 字符）");
            }
        }
    }

    public record Response(List<SearchResultItem> results, int totalHits) {}

    public record SearchResultItem(String title, String text, double score) {}

    // ==================== 实例字段 ====================

    private final HybridSearchService searchService;
    private final List<Long> spaceIds;
    private final String category;

    /** 跨多次调用的累计结果（去重），供最终组装 ChatResponse.sources */
    private final Map<Long, SearchResult> resultCache = new LinkedHashMap<>();

    public SearchKnowledgeTool(HybridSearchService searchService,
                               List<Long> spaceIds, String category) {
        this.searchService = searchService;
        this.spaceIds = (spaceIds != null) ? spaceIds : List.of();
        this.category = (category != null && !category.isBlank()) ? category : null;
    }

    // ==================== Function 接口实现 ====================

    @Override
    public Response apply(Request request) {
        List<SearchResult> hits = searchService.search(
                request.query(),
                adaptiveTopK(request.query()),
                spaceIds,
                category);

        List<SearchResultItem> items = new ArrayList<>();
        for (SearchResult sr : hits) {
            items.add(new SearchResultItem(
                    sr.getDocumentTitle() != null ? sr.getDocumentTitle() : "",
                    truncate(sr.getContent(), 600),
                    sr.getScore() != null ? sr.getScore() : 0.0));
            // 累计去重（documentId 为 null 时用 hashCode 兜底）
            if (sr.getDocumentId() != null) {
                resultCache.putIfAbsent(sr.getDocumentId(), sr);
            }
        }

        return new Response(items, items.size());
    }

    // ==================== 公开方法 ====================

    /** 返回本次 Agent 会话中所有搜索到的文档（去重，保持首次出现顺序） */
    public List<SearchResult> getCachedResults() {
        return new ArrayList<>(resultCache.values());
    }

    // ==================== 内部方法 ====================

    /** 自适应 TopK：短词 8 条，长句 10 条 */
    private int adaptiveTopK(String query) {
        return query.length() > 30 ? 10 : 8;
    }

    /** 截断文本，防止多次搜索后 Token 爆炸 */
    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}
