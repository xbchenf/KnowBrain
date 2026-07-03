package com.knowbrain.retrieval.engine;

import com.knowbrain.document.entity.EkDocument;
import com.knowbrain.document.mapper.EkDocumentMapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam.MetricType;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务 — Dense (语义 COSINE) + Sparse (BM25 jieba) → WeightedRanker 融合
 *
 * 利用 Milvus 2.4 内置 BM25 + jieba 分词器，无需额外部署 ES。
 */
@Slf4j
@Service
public class HybridSearchService {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingModel embeddingModel;
    private final EkDocumentMapper documentMapper;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.hybrid.dense-weight:0.6}")
    private float denseWeight;

    @Value("${milvus.hybrid.sparse-weight:0.4}")
    private float sparseWeight;

    @Value("${milvus.hybrid.analyzer:chinese}")
    private String analyzer;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension}")
    private int dimension;

    public HybridSearchService(MilvusClientV2 milvusClient,
                               EmbeddingModel embeddingModel,
                               EkDocumentMapper documentMapper) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.documentMapper = documentMapper;
    }

    /**
     * 混合检索 Top-K（已废弃，请使用带 spaceIds 的重载方法）
     */
    @Deprecated
    public List<SearchResult> search(String query, int topK) {
        return search(query, topK, Collections.emptyList());
    }

    /**
     * 混合检索 Top-K（带空间权限过滤）
     *
     * @param query    用户问题
     * @param topK     返回结果数
     * @param spaceIds 用户可访问的空间 ID 列表（空列表 = 不过滤）
     */
    @Retryable(retryFor = Exception.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1000))
    public List<SearchResult> search(String query, int topK, List<Long> spaceIds) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 多拉一些候选，补偿后置过滤的损失
        int fetchK = spaceIds != null && !spaceIds.isEmpty() ? topK * 3 : topK;

        // 1. Dense 向量检索准备
        float[] queryEmbedding = embedQuery(query);
        FloatVec denseVec = new FloatVec(toList(queryEmbedding));

        // 2. Sparse 向量（BM25）检索准备
        SparseFloatVec sparseVec = buildQuerySparseVector(query);

        // 3. 构建两次 ANN 搜索
        AnnSearchReq denseSearch = AnnSearchReq.builder()
                .vectorFieldName("dense_vector")
                .vectors(List.of(denseVec))
                .metricType(MetricType.COSINE)
                .topK(fetchK)
                .params("{\"ef\":128}")
                .build();

        AnnSearchReq sparseSearch = AnnSearchReq.builder()
                .vectorFieldName("sparse_vector")
                .vectors(List.of(sparseVec))
                .metricType(MetricType.IP)
                .topK(fetchK)
                .params("{\"drop_ratio_search\":0.1}")
                .build();

        // 4. 混合检索 + 加权融合
        HybridSearchReq hybridReq = HybridSearchReq.builder()
                .collectionName(collectionName)
                .searchRequests(List.of(denseSearch, sparseSearch))
                .ranker(new WeightedRanker(List.of(denseWeight, sparseWeight)))
                .topK(fetchK)
                .outFields(List.of("content", "document_id", "title", "chunk_index"))
                .build();

        SearchResp resp = milvusClient.hybridSearch(hybridReq);

        // 5. 映射结果 + 权限过滤
        boolean filterBySpace = spaceIds != null && !spaceIds.isEmpty();
        List<SearchResult> results = new ArrayList<>();
        if (resp.getSearchResults() != null) {
            for (List<SearchResp.SearchResult> batch : resp.getSearchResults()) {
                if (batch == null) continue;
                for (SearchResp.SearchResult sr : batch) {
                    Map<String, Object> entity = sr.getEntity();
                    if (entity == null) continue;

                    String docIdStr = stringValue(entity.get("document_id"));
                    if (docIdStr == null || docIdStr.isEmpty()) continue;

                    Long docId;
                    try {
                        docId = Long.parseLong(docIdStr);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }

                    // 查 MySQL：验证文档存在 + 获取 spaceId
                    EkDocument ekDoc = documentMapper.selectById(docId);
                    if (ekDoc == null) continue;

                    // 空间权限过滤
                    if (filterBySpace && !spaceIds.contains(ekDoc.getSpaceId())) {
                        continue;
                    }

                    SearchResult result = new SearchResult();
                    result.setContent(stringValue(entity.get("content")));
                    result.setDocumentTitle(stringValue(entity.get("title")));
                    result.setDocumentId(docId);
                    result.setChunkIndex(intValue(entity.get("chunk_index")));
                    result.setScore(sr.getScore() != null ? sr.getScore().doubleValue() : 0.0);
                    results.add(result);

                    // 达到目标数量停止
                    if (results.size() >= topK) break;
                }
                if (results.size() >= topK) break;
            }
        }

        log.debug("混合检索完成: query=\"{}\", {} 条结果 (filterBySpace={})",
                query, results.size(), filterBySpace);
        return results;
    }

    // ==================== 辅助方法 ====================

    /**
     * 用 EmbeddingModel 生成查询向量
     */
    private float[] embedQuery(String query) {
        return embeddingModel.embed(query);
    }

    /**
     * 用 TextTokenizer 对查询文本做中英文分词，构建 SparseFloatVec
     */
    private SparseFloatVec buildQuerySparseVector(String query) {
        com.google.gson.JsonObject tokenObj = TextTokenizer.tokenize(query);
        SortedMap<Long, Float> sparseMap = new TreeMap<>();
        for (String key : tokenObj.keySet()) {
            try {
                sparseMap.put(Long.parseLong(key), tokenObj.get(key).getAsFloat());
            } catch (NumberFormatException ignored) {
                // skip non-numeric keys (shouldn't happen)
            }
        }
        if (sparseMap.isEmpty()) {
            // 兜底：给一个占位项保证查询不报错
            sparseMap.put(0L, 1.0f);
        }
        return new SparseFloatVec(sparseMap);
    }

    @SuppressWarnings("unchecked")
    private List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }

    private String stringValue(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private int intValue(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
