package com.knowbrain.config;

import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.IndexParam.IndexType;
import io.milvus.v2.common.IndexParam.MetricType;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.request.CreateCollectionReq.CollectionSchema;
import io.milvus.v2.service.collection.request.CreateCollectionReq.Function;
import io.milvus.v2.service.index.request.CreateIndexReq;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Milvus Collection Schema 管理器
 *
 * 启动时检查 Collection 是否存在，若不存在则创建带 BM25 混合检索支持的 Schema：
 * - dense_vector (1024d) → HNSW + COSINE（语义检索）
 * - sparse_vector → SPARSE_INVERTED_INDEX + BM25 + jieba 分词（关键词检索）
 */
@Slf4j
@Component
public class MilvusSchemaInitializer {

    private final MilvusClientV2 client;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension}")
    private int dimension;

    @Value("${milvus.hybrid.analyzer:chinese}")
    private String analyzer;

    @Value("${milvus.hybrid.recreate-on-startup:false}")
    private boolean recreateOnStartup;

    public MilvusSchemaInitializer(MilvusClientV2 client) {
        this.client = client;
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = client.hasCollection(
                    HasCollectionReq.builder().collectionName(collectionName).build());

            if (exists && recreateOnStartup) {
                log.info("重建 Milvus Collection: {} (recreate-on-startup=true)", collectionName);
                client.dropCollection(DropCollectionReq.builder()
                        .collectionName(collectionName).build());
                exists = false;
            }

            if (exists) {
                log.info("Milvus Collection '{}' 已存在，跳过创建", collectionName);
                ensureLoaded();
                return;
            }

            log.info("创建 Milvus Collection: {} (dim={}, analyzer={})", collectionName, dimension, analyzer);
            createCollectionWithBM25();
            createIndexes();
            loadCollection();
            log.info("Milvus Collection '{}' 初始化完成（混合检索就绪）", collectionName);

        } catch (Exception e) {
            log.error("Milvus Schema 初始化失败: {}", e.getMessage(), e);
        }
    }

    // ==================== Collection 创建 ====================

    private void createCollectionWithBM25() {
        // 1. 定义字段
        CollectionSchema schema = MilvusClientV2.CreateSchema();
        schema.setEnableDynamicField(false);

        // 主键
        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());

        // 切片文本 — 启用 jieba 中文分词器
        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .enableAnalyzer(true)
                .analyzerParams(Map.of("type", (Object) analyzer))
                .build());

        // Dense 向量 — 语义检索
        schema.addField(AddFieldReq.builder()
                .fieldName("dense_vector")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());

        // Sparse 向量 — BM25 关键词检索（由 Function 自动生成）
        schema.addField(AddFieldReq.builder()
                .fieldName("sparse_vector")
                .dataType(DataType.SparseFloatVector)
                .build());

        // 元数据字段
        schema.addField(AddFieldReq.builder()
                .fieldName("document_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("title")
                .dataType(DataType.VarChar)
                .maxLength(512)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_index")
                .dataType(DataType.VarChar)
                .maxLength(16)
                .build());

        // 2. 添加 BM25 Function（content → sparse_vector）
        schema.addFunction(Function.builder()
                .name("bm25_func")
                .functionType(FunctionType.BM25)
                .inputFieldNames(List.of("content"))
                .outputFieldNames(List.of("sparse_vector"))
                .build());

        // 3. 创建 Collection
        CreateCollectionReq req = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .build();

        client.createCollection(req);
        log.info("Milvus Collection Schema 创建完成: {}", collectionName);
    }

    // ==================== 索引创建 ====================

    private void createIndexes() {
        // Dense 向量索引 — HNSW + COSINE
        IndexParam denseParam = IndexParam.builder()
                .fieldName("dense_vector")
                .indexType(IndexType.HNSW)
                .metricType(MetricType.COSINE)
                .extraParams(Map.of("M", (Object) 16, "efConstruction", (Object) 200))
                .build();

        // Sparse 向量索引 — SPARSE_INVERTED_INDEX + IP（Milvus 要求 IP）
        IndexParam sparseParam = IndexParam.builder()
                .fieldName("sparse_vector")
                .indexType(IndexType.SPARSE_INVERTED_INDEX)
                .metricType(MetricType.IP)
                .extraParams(Map.of("drop_ratio_build", (Object) 0.2))
                .build();

        CreateIndexReq req = CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(List.of(denseParam, sparseParam))
                .build();

        client.createIndex(req);
        log.info("Milvus 索引创建完成: dense=HNSW+COSINE, sparse=SPARSE_INVERTED_INDEX+BM25");
    }

    // ==================== Collection 加载 ====================

    private void loadCollection() {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build());
        log.info("Milvus Collection '{}' 已加载到内存", collectionName);
    }

    private void ensureLoaded() {
        try {
            var state = client.getLoadState(GetLoadStateReq.builder()
                    .collectionName(collectionName).build());
            if (state == null || !state) {
                loadCollection();
            }
        } catch (Exception e) {
            log.warn("检查 Load State 失败，尝试重新加载: {}", e.getMessage());
            loadCollection();
        }
    }
}
