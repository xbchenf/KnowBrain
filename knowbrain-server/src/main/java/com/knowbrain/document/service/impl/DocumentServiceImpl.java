package com.knowbrain.document.service.impl;

import com.knowbrain.document.entity.EkDocument;
import com.knowbrain.document.entity.EkDocumentChunk;
import com.knowbrain.document.mapper.EkDocumentChunkMapper;
import com.knowbrain.document.mapper.EkDocumentMapper;
import com.knowbrain.document.service.DocumentService;
import com.knowbrain.document.service.SmartChunker;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import com.knowbrain.retrieval.engine.RAGCacheService;
import com.knowbrain.retrieval.engine.TextTokenizer;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档管理服务 — 上传 → Tika 解析 → SmartChunker 切片 → Milvus 向量化
 * 从 EICS V2.0 迁移适配
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final EkDocumentMapper documentMapper;
    private final EkDocumentChunkMapper chunkMapper;
    private final MinioClient minioClient;
    private final SmartChunker chunker;
    private final MilvusClientV2 milvusClient;
    private final EmbeddingModel embeddingModel;
    private final RAGCacheService cacheService;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension}")
    private int dimension;

    public DocumentServiceImpl(EkDocumentMapper documentMapper,
                               EkDocumentChunkMapper chunkMapper,
                               MinioClient minioClient,
                               SmartChunker chunker,
                               MilvusClientV2 milvusClient,
                               EmbeddingModel embeddingModel,
                               RAGCacheService cacheService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.minioClient = minioClient;
        this.chunker = chunker;
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.cacheService = cacheService;
    }

    @Override
    public EkDocument upload(MultipartFile file, Long spaceId, Long uploaderId, String category) {
        ensureBucket();

        String objectKey = uploadToMinIO(file);

        EkDocument doc = new EkDocument();
        doc.setTitle(file.getOriginalFilename());
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(getFileType(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setMinioPath(objectKey);
        doc.setSpaceId(spaceId);
        doc.setUploaderId(uploaderId);
        doc.setCategory(category);
        doc.setStatus("PARSING");
        documentMapper.insert(doc);
        log.info("文档入库: id={}, name={}, spaceId={}, category={}",
                doc.getId(), doc.getFileName(), spaceId, category);

        try {
            parseAndVectorize(doc, file);
            cacheService.invalidateAll(); // 新文档入库，清除旧缓存
        } catch (Exception e) {
            log.error("文档处理失败: id={}", doc.getId(), e);
            doc.setStatus("FAILED");
            documentMapper.updateById(doc);
        }
        return doc;
    }

    @Override
    public EkDocument getById(Long id) {
        return documentMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        documentMapper.deleteById(id);
        cacheService.invalidateAll();
        log.info("文档已删除: id={}", id);
    }

    @Override
    public void chunkAndVectorize(Long documentId) {
        EkDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("文档不存在: id={}", documentId);
            return;
        }
        if (doc.getParsedContent() == null || doc.getParsedContent().isEmpty()) {
            log.warn("文档未解析或无内容: id={}", documentId);
            return;
        }
        log.info("重新切片+向量化: docId={}", documentId);
        vectorizeDocument(doc);
    }

    // ==================== 核心流水线 ====================

    /**
     * Tika 解析 → SmartChunker 切片 → Milvus 向量化
     */
    private void parseAndVectorize(EkDocument doc, MultipartFile file) {
        String fullText = parseWithTika(file);
        doc.setParsedContent(fullText);
        documentMapper.updateById(doc);
        log.info("Tika 解析完成: docId={}, {} 字符", doc.getId(), fullText.length());

        vectorizeDocument(doc);
    }

    /**
     * 切片 + 写入 MySQL + 写入 Milvus（Dense + Sparse 向量）
     */
    private void vectorizeDocument(EkDocument doc) {
        String text = doc.getParsedContent();
        if (text == null || text.isEmpty()) {
            doc.setStatus("FAILED");
            documentMapper.updateById(doc);
            return;
        }

        List<String> chunks = chunker.chunk(text);
        log.info("切片完成: docId={}, {} 片", doc.getId(), chunks.size());

        if (chunks.isEmpty()) {
            doc.setStatus("FAILED");
            documentMapper.updateById(doc);
            return;
        }

        // 批量分析器调用：一次性对全部切片做 jieba 分词
        List<JsonObject> sparseVecs = buildSparseVectors(chunks);

        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            // 写入 MySQL
            EkDocumentChunk chunk = new EkDocumentChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setCharCount(chunkText.length());
            chunkMapper.insert(chunk);

            // 生成 Dense Embedding
            float[] embedding = embeddingModel.embed(chunkText);

            // 构建 Milvus 插入数据
            JsonObject row = new JsonObject();
            row.addProperty("content", chunkText);

            JsonArray denseArr = new JsonArray();
            for (float v : embedding) {
                denseArr.add(v);
            }
            row.add("dense_vector", denseArr);
            // sparse_vector: runAnalyzer 生成的 term-hash → 词频映射
            row.add("sparse_vector", sparseVecs.get(i));

            row.addProperty("document_id", doc.getId().toString());
            row.addProperty("title", doc.getTitle());
            row.addProperty("chunk_index", String.valueOf(i));

            rows.add(row);
        }

        // 批量插入 Milvus
        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build();
        milvusClient.insert(insertReq);
        log.info("向量写入 Milvus 完成: docId={}, {} 条 (dense={}d, sparse=BM25 auto)",
                doc.getId(), rows.size(), dimension);

        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);
    }

    /**
     * 对切片文本做中英文分词，构建 Milvus SparseFloatVector
     * 返回与 chunks 一一对应的稀疏向量（term-hash → 词频）
     */
    private List<JsonObject> buildSparseVectors(List<String> chunks) {
        List<JsonObject> result = new ArrayList<>();
        for (String chunk : chunks) {
            result.add(TextTokenizer.tokenize(chunk));
        }
        return result;
    }

    // ==================== Tika 文本提取 ====================

    private String parseWithTika(MultipartFile file) {
        Path tempFile = null;
        try {
            String originalName = file.getOriginalFilename();
            String suffix = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".tmp";
            tempFile = Files.createTempFile("knowbrain-", suffix);
            file.transferTo(tempFile.toFile());

            TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(tempFile.toFile()));
            return reader.get().stream()
                    .map(Document::getContent)
                    .reduce("", (a, b) -> a + "\n" + b);
        } catch (Exception e) {
            throw new RuntimeException("Tika 解析失败", e);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== MinIO 操作 ====================

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO Bucket '{}' 已创建", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO Bucket 初始化失败: " + bucket, e);
        }
    }

    private String uploadToMinIO(MultipartFile file) {
        try {
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String safeName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._\\-]", "_");
            String objectKey = dateDir + "/" + UUID.randomUUID().toString().substring(0, 8)
                    + "_" + safeName;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(file.getBytes()), file.getSize(), -1)
                    .contentType(file.getContentType() != null
                            ? file.getContentType() : "application/octet-stream")
                    .build());
            log.info("文件已上传 MinIO: bucket={}, key={}", bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败", e);
        }
    }

    // ==================== 工具方法 ====================

    private String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String name = fileName.toLowerCase();
        if (name.endsWith(".pdf")) return "pdf";
        if (name.endsWith(".docx") || name.endsWith(".doc")) return "docx";
        if (name.endsWith(".txt")) return "txt";
        if (name.endsWith(".md")) return "md";
        return "unknown";
    }
}
