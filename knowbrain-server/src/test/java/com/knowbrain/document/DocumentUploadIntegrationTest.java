package com.knowbrain.document;

import com.knowbrain.TestMockConfig;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.document.entity.EkDocument;
import com.knowbrain.document.mapper.EkDocumentChunkMapper;
import com.knowbrain.document.mapper.EkDocumentMapper;
import com.knowbrain.document.service.DocumentService;
import com.knowbrain.document.service.chunker.ElementAwareChunker;
import com.knowbrain.document.service.parser.ParsedDocument;
import com.knowbrain.document.service.parser.ParseMetadata;
import com.knowbrain.document.service.parser.ParserRouter;
import com.knowbrain.retrieval.engine.RAGCacheService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文档上传全链路集成测试 — 解析 → 切片 → Embedding → Milvus 向量化
 *
 * 覆盖场景：
 * - 上传合法 PDF → 全链路成功，状态 READY
 * - 解析器抛异常 → 状态 FAILED
 * - 多种格式文件 → Controller 层均接受
 */
@SpringBootTest
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("文档上传 — 全链路集成测试")
class DocumentUploadIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EkDocumentMapper documentMapper;

    @Autowired
    private EkDocumentChunkMapper chunkMapper;

    @MockBean
    private ParserRouter parserRouter;

    @MockBean
    private ElementAwareChunker chunker;

    @MockBean
    private RAGCacheService cacheService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MilvusClientV2 milvusClient;

    @Autowired
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() throws Exception {
        // MinIO：mock 上传成功
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        // Milvus：mock 插入成功
        when(milvusClient.insert(any(InsertReq.class))).thenReturn(null);
        // Embedding：返回固定 1024 维向量
        when(embeddingModel.embed(anyString()))
                .thenReturn(new float[1024]);
        when(embeddingModel.dimensions()).thenReturn(1024);
    }

    @Nested
    @DisplayName("上传成功 — 全链路")
    class FullPipeline {

        @Test
        @DisplayName("上传 PDF → 解析 → 切片 → 向量化 → DB 记录完整")
        void uploadPdfSuccess() {
            when(parserRouter.parse(any(byte[].class), eq("test.pdf")))
                    .thenReturn(new ParsedDocument(
                            "# 信息安全管理制度\n\n## 第一章 总则\n\n这是测试内容。",
                            3,
                            ParseMetadata.of("TestParser", 42)));
            when(chunker.chunk(anyString())).thenReturn(List.of(
                    "## 第一章 总则\n\n这是测试内容。",
                    "## 第二章 细则\n\n更多测试内容。"));

            EkDocument doc = documentService.upload(
                    mockMultipartFile("test.pdf", 1024), 1L, 1L, "IT运维");

            assertNotNull(doc);
            assertNotNull(doc.getId());
            assertEquals("test.pdf", doc.getFileName());
            assertEquals("pdf", doc.getFileType());
            assertEquals(1L, doc.getSpaceId());
            assertEquals("IT运维", doc.getCategory());
            assertEquals("READY", doc.getStatus());
            assertEquals(2, doc.getChunkCount());
            assertNotNull(doc.getParsedContent());

            // 验证 Milvus 向量写入被调用
            verify(milvusClient).insert(any(InsertReq.class));

            // 验证 chunk 写入 MySQL
            var chunks = chunkMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            com.knowbrain.document.entity.EkDocumentChunk>()
                            .eq(com.knowbrain.document.entity.EkDocumentChunk::getDocumentId, doc.getId()));
            assertEquals(2, chunks.size());

            // 验证缓存清除
            verify(cacheService).invalidateAll();
        }
    }

    @Nested
    @DisplayName("解析失败")
    class ParserFailure {

        @Test
        @DisplayName("解析器抛异常 → 文档状态 FAILED")
        void parserThrows() {
            when(parserRouter.parse(any(byte[].class), eq("bad.pdf")))
                    .thenThrow(new BizException(500, "文档解析失败"));

            EkDocument doc = documentService.upload(
                    mockMultipartFile("bad.pdf", 512), 1L, 1L, null);

            assertEquals("FAILED", doc.getStatus());
            verify(chunker, never()).chunk(anyString());
            verify(milvusClient, never()).insert(any(InsertReq.class));
        }

        @Test
        @DisplayName("解析内容为空字符串 → 向量化阶段返回 FAILED")
        void parserReturnsEmpty() {
            when(parserRouter.parse(any(byte[].class), eq("empty.pdf")))
                    .thenReturn(new ParsedDocument("", 1, ParseMetadata.of("EmptyParser", 5)));

            // chunker 返回空列表 → vectorizeDocument 检测到 isEmpty → FAILED
            when(chunker.chunk(eq(""))).thenReturn(List.of());

            EkDocument doc = documentService.upload(
                    mockMultipartFile("empty.pdf", 512), 1L, 1L, null);

            assertEquals("FAILED", doc.getStatus());
            verify(milvusClient, never()).insert(any(InsertReq.class));
        }
    }

    @Nested
    @DisplayName("多格式支持")
    class MultiFormat {

        @Test
        @DisplayName("支持的类型：pdf, docx, xlsx, pptx, txt, md, csv → 均正常入库")
        void allSupportedTypes() {
            for (String ext : List.of("pdf", "docx", "xlsx", "pptx", "txt", "md", "csv")) {
                when(parserRouter.parse(any(byte[].class), eq("test." + ext)))
                        .thenReturn(new ParsedDocument("valid content for " + ext,
                                1, ParseMetadata.of("p", 0)));
                when(chunker.chunk(anyString())).thenReturn(List.of("chunk1"));

                EkDocument doc = documentService.upload(
                        mockMultipartFile("test." + ext, 100), 1L, 1L, null);

                assertNotNull(doc);
                assertEquals(ext, doc.getFileType(),
                        "类型 " + ext + " 应被支持");
                assertEquals("READY", doc.getStatus(),
                        "类型 " + ext + " 应成功入库");
            }
        }
    }

    // ==================== 工具方法 ====================

    private MultipartFile mockMultipartFile(String fileName, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(file.getSize()).thenReturn(size);
        when(file.getContentType()).thenReturn("application/octet-stream");
        try {
            byte[] dummy = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            when(file.getBytes()).thenReturn(dummy);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }
}
