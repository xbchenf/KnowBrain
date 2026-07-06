package com.knowbrain.retrieval.engine;

import com.knowbrain.TestMockConfig;
import com.knowbrain.common.RequestContext;
import com.knowbrain.statistics.SearchLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 测试 4-5, 8：RAG 检索 + 生成 + 降级回退
 *
 * 对 LLM 调用链（ChatClient → prompt → user → stream → content → Flux）的 mock
 * 需要精确匹配 Spring AI 1.0.0-M4 的 API 类型层次，此处用简化的集成测试替代，
 * 重点覆盖：FAQ 命中短路 / 检索无结果兜底 / 纯检索委托。
 * LLM 调用成功和降级的完整 mock 留待后续版本补充。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
@DisplayName("RAG 问答服务")
class RAGServiceTest {

    @Autowired
    private RAGService ragService;

    @MockBean
    private HybridSearchService hybridSearchService;

    @MockBean
    private ChatClient.Builder chatClientBuilder;

    @MockBean
    private ChatClient chatClient;

    @MockBean
    private RAGCacheService cacheService;

    @MockBean
    private SearchLogMapper searchLogMapper;

    @MockBean
    private RequestContext requestContext;

    @BeforeEach
    void setUpMocks() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(cacheService.get(any(), any())).thenReturn(null);
    }

    @Nested
    @DisplayName("FAQ 命中")
    class FaqHit {

        @Test
        @DisplayName("FAQ 命中时跳过检索和 LLM，直接返回预设答案")
        void shouldReturnPresetAnswerOnFaqHit() {
            ChatResponse response = ragService.chat("我有多少天年假？",
                    Collections.emptyList(), Collections.emptyList(), null);

            assertNotNull(response);
            assertFalse(response.isFallback());
            assertEquals("high", response.getConfidence());
            assertTrue(response.getAnswer().contains("年假"));
            assertFalse(response.getSources().isEmpty());
            assertTrue(response.getSources().get(0).getTitle().contains("知识库预设问答"));
        }
    }

    @Nested
    @DisplayName("无搜索结果")
    class EmptySearch {

        @Test
        @DisplayName("检索无结果时返回兜底回答")
        void shouldReturnFallbackOnEmptySearch() {
            when(hybridSearchService.search(any(), any(Integer.class), any(), any()))
                    .thenReturn(Collections.emptyList());

            ChatResponse response = ragService.chat("xyz完全不相关的查询abc123",
                    Collections.emptyList(), Collections.emptyList(), null);

            assertNotNull(response);
            assertTrue(response.isFallback());
            assertEquals("low", response.getConfidence());
            assertTrue(response.getAnswer().contains("未找到"));
        }
    }

    @Nested
    @DisplayName("纯检索")
    class PureSearch {

        @Test
        @DisplayName("search() 应委托给 HybridSearchService")
        void shouldDelegateToHybridSearch() {
            SearchResult sr = new SearchResult("内容", "标题", 1L, 0, 0.9);
            when(hybridSearchService.search(any(), any(Integer.class), any(), any()))
                    .thenReturn(List.of(sr));

            List<SearchResult> results = ragService.search("测试查询", 5);
            assertFalse(results.isEmpty());
            assertEquals("标题", results.get(0).getDocumentTitle());
        }
    }
}
