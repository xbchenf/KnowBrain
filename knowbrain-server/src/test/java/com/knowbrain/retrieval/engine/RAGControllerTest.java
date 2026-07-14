package com.knowbrain.retrieval.engine;

import com.knowbrain.TestMockConfig;
import com.knowbrain.auth.JwtUtil;
import com.knowbrain.auth.TokenBlacklistService;
import com.knowbrain.auth.UserService;
import com.knowbrain.common.RateLimiter;
import com.knowbrain.permission.PermissionService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RAG 问答 Controller 端点测试 — /api/v1/rag
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DisplayName("RAGController — 问答接口")
class RAGControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RAGService ragService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private RateLimiter rateLimiter;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private TokenBlacklistService blacklistService;

    @MockBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        // 认证 mock
        when(jwtUtil.getJti(anyString())).thenReturn("test-jti");
        when(blacklistService.isBlacklisted(anyString())).thenReturn(false);
        Map<String, Object> claims = Map.of(
                "userId", 1L, "username", "admin", "role", "ADMIN",
                "iat", System.currentTimeMillis() / 1000 - 60);
        when(jwtUtil.verifyToken(anyString())).thenReturn(claims);
        when(userService.getTokenInvalidBefore(anyLong())).thenReturn(0L);

        // 限流 + 权限
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);
        when(permissionService.getAccessibleSpaceIds(anyLong()))
                .thenReturn(List.of(1L, 2L));
    }

    // ---- helpers ----

    /** 构造带认证头 + userId 的 POST 请求 */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authPost(String url, String json) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer fake-token")
                .content(json);
    }

    /** 构造带认证头的 GET 请求 */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authGet(String url) {
        return get(url).header("Authorization", "Bearer fake-token");
    }

    // ==================== /chat ====================

    @Nested
    @DisplayName("POST /api/v1/rag/chat")
    class Chat {

        @Test
        @DisplayName("有效问题 → 200 + 答案")
        void chatSuccess() throws Exception {
            ChatResponse response = new ChatResponse(
                    "这是AI生成的回答。",
                    List.of(new ChatResponse.SourceInfo("文档标题", 1L, 0, "相关内容...")),
                    false, "high");
            when(ragService.chat(eq("VPN怎么配置？"), anyList(), anyList(), isNull()))
                    .thenReturn(response);

            mockMvc.perform(authPost("/api/v1/rag/chat",
                            "{\"question\":\"VPN怎么配置？\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.answer").value("这是AI生成的回答。"))
                    .andExpect(jsonPath("$.data.confidence").value("high"));
        }

        @Test
        @DisplayName("空问题 → 400")
        void chatEmptyQuestion() throws Exception {
            mockMvc.perform(authPost("/api/v1/rag/chat",
                            "{\"question\":\"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("带 spaceIds → 过滤后搜索")
        void chatWithSpaceIds() throws Exception {
            ChatResponse response = new ChatResponse("答案", Collections.emptyList(), false, "medium");
            when(ragService.chat(eq("问题"), eq(List.of(1L)), anyList(), isNull()))
                    .thenReturn(response);

            mockMvc.perform(authPost("/api/v1/rag/chat",
                            "{\"question\":\"问题\",\"spaceIds\":[1]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("限流触发 → 429")
        void chatRateLimited() throws Exception {
            when(rateLimiter.tryAcquire(anyLong())).thenReturn(false);

            mockMvc.perform(authPost("/api/v1/rag/chat",
                            "{\"question\":\"VPN怎么配置？\"}"))
                    .andExpect(status().is(429));
        }
    }

    // ==================== /search ====================

    @Nested
    @DisplayName("GET /api/v1/rag/search")
    class Search {

        @Test
        @DisplayName("有效搜索 → 200 + 结果列表")
        void searchSuccess() throws Exception {
            SearchResult sr = new SearchResult("片段", "标题", 1L, 0, 0.95);
            when(ragService.search(eq("测试"), eq(5), anyList()))
                    .thenReturn(List.of(sr));

            mockMvc.perform(authGet("/api/v1/rag/search?q=测试&topK=5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].documentTitle").value("标题"));
        }

        @Test
        @DisplayName("空搜索 → 400")
        void searchEmptyQuery() throws Exception {
            mockMvc.perform(authGet("/api/v1/rag/search?q="))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("无结果 → 200 + 空列表")
        void searchNoResults() throws Exception {
            when(ragService.search(anyString(), anyInt(), anyList()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(authGet("/api/v1/rag/search?q=xyz不存在"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("限流触发 → 429")
        void searchRateLimited() throws Exception {
            when(rateLimiter.tryAcquire(anyLong())).thenReturn(false);

            mockMvc.perform(authGet("/api/v1/rag/search?q=测试"))
                    .andExpect(status().is(429));
        }
    }
}
