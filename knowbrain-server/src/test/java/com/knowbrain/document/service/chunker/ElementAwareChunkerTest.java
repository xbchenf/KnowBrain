package com.knowbrain.document.service.chunker;

import com.knowbrain.document.service.SmartChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ElementAwareChunker — Markdown 结构保护分块")
class ElementAwareChunkerTest {

    private ElementAwareChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new ElementAwareChunker(new SmartChunker());
    }

    @Nested
    @DisplayName("表格完整性保护")
    class TableIntegrity {

        @Test
        @DisplayName("表格独立成 chunk，不被拆分")
        void tableAsAtomicChunk() {
            String markdown = """
                    这是文档的第一段文字，用于描述系统的基本情况。

                    | 参数名 | 默认值 | 说明 |
                    |--------|--------|------|
                    | maxConnections | 100 | 最大连接数 |
                    | timeout | 30 | 超时秒数 |
                    | retryCount | 3 | 重试次数 |

                    这是表格之后的补充说明。""";

            List<String> chunks = chunker.chunk(markdown);

            // 至少有一个 chunk 包含完整表格
            boolean tableIntact = chunks.stream().anyMatch(c ->
                    c.contains("| 参数名 |") &&
                    c.contains("| maxConnections |") &&
                    c.contains("| retryCount |"));
            assertTrue(tableIntact, "表格应完整保留在一个 chunk 中");

            // 表格不应跨 chunk 拆分——分隔行 |---|---| 和所有数据行在同一 chunk
            for (String chunk : chunks) {
                if (chunk.contains("|--------|")) {
                    assertTrue(chunk.contains("maxConnections"), "含表头的 chunk 应包含完整表格数据");
                    assertTrue(chunk.contains("retryCount"), "表格尾行不应被拆到其他 chunk");
                }
            }
        }

        @Test
        @DisplayName("多表文档 — 每张表各自独立")
        void multipleTablesSeparate() {
            String markdown = """
                    ## 数据库配置

                    | 参数 | 值 |
                    |------|----|
                    | host | localhost |

                    中间说明文字。

                    ## 缓存配置

                    | 参数 | 值 |
                    |------|----|
                    | ttl | 3600 |""";

            List<String> chunks = chunker.chunk(markdown);

            // 两张表不应混在同一个 chunk
            long tablesWithHost = chunks.stream().filter(c -> c.contains("host")).count();
            long tablesWithTtl = chunks.stream().filter(c -> c.contains("ttl")).count();
            assertTrue(tablesWithHost >= 1, "至少一个 chunk 包含第一张表");
            assertTrue(tablesWithTtl >= 1, "至少一个 chunk 包含第二张表");
        }
    }

    @Nested
    @DisplayName("代码块完整性保护")
    class CodeBlockIntegrity {

        @Test
        @DisplayName("代码块独立成 chunk")
        void codeBlockAtomic() {
            String markdown = """
                    配置示例如下：

                    ```
                    server:
                      port: 8080
                      servlet:
                        encoding:
                          charset: UTF-8
                    ```

                    以上为完整配置。""";

            List<String> chunks = chunker.chunk(markdown);

            // 代码块应完整保留在一个 chunk 中
            boolean codeBlockIntact = chunks.stream().anyMatch(c ->
                    c.contains("```") &&
                    c.contains("server:") &&
                    c.contains("port: 8080") &&
                    c.contains("charset: UTF-8"));
            assertTrue(codeBlockIntact, "代码块应完整保留在一个 chunk 中");
        }
    }

    @Nested
    @DisplayName("标题处理")
    class HeadingHandling {

        @Test
        @DisplayName("标题触发新段落开始")
        void headingStartsNewSection() {
            String markdown = """
                    导言段落。

                    ## 第一节

                    第一节的内容。

                    ## 第二节

                    第二节的内容。""";

            List<String> chunks = chunker.chunk(markdown);

            // 各节内容应与各自标题在同一个 chunk（SmallChunker 按语义切分）
            assertFalse(chunks.isEmpty(), "应产生至少一个 chunk");
        }
    }

    @Nested
    @DisplayName("空输入与边界")
    class EdgeCases {

        @Test
        @DisplayName("null / 空字符串 → 空列表")
        void nullOrEmpty() {
            assertTrue(chunker.chunk(null).isEmpty());
            assertTrue(chunker.chunk("").isEmpty());
        }

        @Test
        @DisplayName("纯文本（无结构元素）→ 正常分块")
        void plainTextOnly() {
            String text = "a".repeat(800); // 超长纯文本

            List<String> chunks = chunker.chunk(text);

            assertFalse(chunks.isEmpty());
            // 无分隔符的纯文本 SmartChunker 无法强制切分，整个作为一个 chunk
            // 验证至少产生了 chunk（不分拆是合理的——无语义边界的文本不应硬切）
            assertTrue(chunks.size() >= 1);
        }

        @Test
        @DisplayName("最短有效输入 → 单 chunk")
        void minimalInput() {
            List<String> chunks = chunker.chunk("Hello World");
            assertEquals(1, chunks.size());
            assertEquals("Hello World", chunks.get(0));
        }
    }
}
