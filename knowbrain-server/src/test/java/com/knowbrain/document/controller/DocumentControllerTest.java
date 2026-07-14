package com.knowbrain.document.controller;

import com.knowbrain.TestMockConfig;
import com.knowbrain.auth.JwtUtil;
import com.knowbrain.auth.TokenBlacklistService;
import com.knowbrain.auth.UserService;
import com.knowbrain.common.RAGMetrics;
import com.knowbrain.document.entity.EkDocument;
import com.knowbrain.document.service.DocumentService;
import com.knowbrain.permission.PermissionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 文档管理 Controller 端点测试 — /api/v1/documents
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DisplayName("DocumentController — 文档管理接口")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private RAGMetrics metrics;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private TokenBlacklistService blacklistService;

    @MockBean
    private UserService userService;

    private EkDocument sampleDoc;

    private static final String AUTH_HEADER = "Bearer fake-token";

    @BeforeEach
    void setUp() {
        // 认证 mock — 让 AuthInterceptor 放行
        when(jwtUtil.getJti(anyString())).thenReturn("test-jti");
        when(blacklistService.isBlacklisted(anyString())).thenReturn(false);
        Map<String, Object> claims = Map.of(
                "userId", 1L, "username", "admin", "role", "ADMIN",
                "iat", System.currentTimeMillis() / 1000 - 60);
        when(jwtUtil.verifyToken(anyString())).thenReturn(claims);
        when(userService.getTokenInvalidBefore(anyLong())).thenReturn(0L);

        sampleDoc = new EkDocument();
        sampleDoc.setId(1L);
        sampleDoc.setTitle("测试文档.pdf");
        sampleDoc.setFileName("测试文档.pdf");
        sampleDoc.setFileType("pdf");
        sampleDoc.setFileSize(10240L);
        sampleDoc.setSpaceId(1L);
        sampleDoc.setUploaderId(1L);
        sampleDoc.setStatus("READY");
        sampleDoc.setChunkCount(3);

        doNothing().when(permissionService).checkWriteAccess(anyLong(), anyLong());
        doNothing().when(permissionService).checkReadAccess(anyLong(), anyLong());
    }

    @Nested
    @DisplayName("POST /api/v1/documents/upload")
    class Upload {

        @Test
        @DisplayName("合法文件 → 200 + 文档记录")
        void uploadSuccess() throws Exception {
            when(documentService.upload(any(), eq(1L), eq(1L), eq("IT运维")))
                    .thenReturn(sampleDoc);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf",
                    "dummy pdf content".getBytes());

            mockMvc.perform(multipart("/api/v1/documents/upload")
                            .file(file)
                            .param("spaceId", "1")
                            .param("category", "IT运维")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.fileName").value("测试文档.pdf"));
        }

        @Test
        @DisplayName("空文件 → 400")
        void uploadEmptyFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            mockMvc.perform(multipart("/api/v1/documents/upload")
                            .file(file)
                            .param("spaceId", "1")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("文件不能为空"));
        }

        @Test
        @DisplayName("不支持格式 → 400")
        void uploadUnsupportedType() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "virus.exe", "application/octet-stream",
                    "x".getBytes());

            mockMvc.perform(multipart("/api/v1/documents/upload")
                            .file(file)
                            .param("spaceId", "1")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(
                            "不支持的文件类型（.exe），仅允许：pdf, docx, doc, xlsx, xls, pptx, ppt, txt, md, csv"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/documents")
    class ListDocs {

        @Test
        @DisplayName("列表查询 → 200 + 分页结果")
        void listSuccess() throws Exception {
            Page<EkDocument> page = new Page<>(1, 20);
            page.setRecords(List.of(sampleDoc));
            page.setTotal(1);
            when(documentService.listBySpace(eq(1L), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get("/api/v1/documents?spaceId=1&page=1&size=20")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].fileName").value("测试文档.pdf"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/documents/{id}")
    class GetDoc {

        @Test
        @DisplayName("文档存在 → 200 + 详情")
        void getByIdSuccess() throws Exception {
            when(documentService.getById(1L)).thenReturn(sampleDoc);

            mockMvc.perform(get("/api/v1/documents/1")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.fileName").value("测试文档.pdf"));
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void getByIdNotFound() throws Exception {
            when(documentService.getById(999L)).thenReturn(null);

            mockMvc.perform(get("/api/v1/documents/999")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/documents/{id}")
    class UpdateMeta {

        @Test
        @DisplayName("更新标题 → 200")
        void updateTitle() throws Exception {
            when(documentService.getById(1L)).thenReturn(sampleDoc);

            mockMvc.perform(put("/api/v1/documents/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", AUTH_HEADER)
                            .content("{\"title\":\"新标题.pdf\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(documentService).updateMeta(eq(1L), eq("新标题.pdf"), isNull());
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void updateNotFound() throws Exception {
            when(documentService.getById(999L)).thenReturn(null);

            mockMvc.perform(put("/api/v1/documents/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", AUTH_HEADER)
                            .content("{\"title\":\"新标题\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/documents/{id}")
    class DeleteDoc {

        @Test
        @DisplayName("正常删除 → 200")
        void deleteSuccess() throws Exception {
            when(documentService.getById(1L)).thenReturn(sampleDoc);

            mockMvc.perform(delete("/api/v1/documents/1")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(documentService).delete(1L);
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void deleteNotFound() throws Exception {
            when(documentService.getById(999L)).thenReturn(null);

            mockMvc.perform(delete("/api/v1/documents/999")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("权限拦截")
    class PermissionCheck {

        @Test
        @DisplayName("无写权限上传 → 403")
        void uploadNoWritePermission() throws Exception {
            doThrow(new com.knowbrain.common.GlobalExceptionHandler.BizException(403, "无权编辑此空间"))
                    .when(permissionService).checkWriteAccess(anyLong(), anyLong());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "data".getBytes());

            mockMvc.perform(multipart("/api/v1/documents/upload")
                            .file(file)
                            .param("spaceId", "1")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().is(403));
        }

        @Test
        @DisplayName("无读权限查看 → 403")
        void getNoReadPermission() throws Exception {
            when(documentService.getById(1L)).thenReturn(sampleDoc);
            doThrow(new com.knowbrain.common.GlobalExceptionHandler.BizException(403, "无权访问此空间"))
                    .when(permissionService).checkReadAccess(anyLong(), anyLong());

            mockMvc.perform(get("/api/v1/documents/1")
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().is(403));
        }
    }
}
