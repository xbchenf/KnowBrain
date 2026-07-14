package com.knowbrain.document.service.table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TableToMarkdown — 表格转 Markdown")
class TableToMarkdownTest {

    @Nested
    @DisplayName("基础转换")
    class BasicConversion {

        @Test
        @DisplayName("简单单表 — 表头 + 数据行")
        void simpleTable() {
            List<List<String>> grid = List.of(
                    List.of("姓名", "年龄", "部门"),
                    List.of("张三", "28", "技术部"),
                    List.of("李四", "35", "产品部")
            );

            String result = TableToMarkdown.convert(grid);

            assertTrue(result.contains("姓名"));
            assertTrue(result.contains("年龄"));
            assertTrue(result.contains("部门"));
            // 分隔行存在（| --- | 格式，带空格）
            assertTrue(result.contains("| -"), "应包含 GFM 表头分隔行，实际: " + result);
            assertTrue(result.contains("张三"));
            assertTrue(result.contains("李四"));
            // 验证列对齐：每行以 | 开头
            for (String line : result.split("\n")) {
                assertTrue(line.trim().startsWith("|"), "每行应以 | 开头: " + line);
                assertTrue(line.trim().endsWith("|"), "每行应以 | 结尾: " + line);
            }
        }

        @Test
        @DisplayName("单列表 — 只有一列")
        void singleColumn() {
            List<List<String>> grid = List.of(
                    List.of("项目"),
                    List.of("需求分析"),
                    List.of("系统设计")
            );

            String result = TableToMarkdown.convert(grid);

            assertTrue(result.contains("项目"));
            assertTrue(result.contains("需求分析"));
            assertTrue(result.contains("系统设计"));
            assertTrue(result.contains("| -"), "应包含表头分隔行: " + result);
        }

        @Test
        @DisplayName("空网格 → 空字符串")
        void emptyGrid() {
            assertEquals("", TableToMarkdown.convert(List.of()));
            assertEquals("", TableToMarkdown.convert(null));
        }
    }

    @Nested
    @DisplayName("特殊字符处理")
    class SpecialCharacters {

        @Test
        @DisplayName("管道符转义 — | 替换为 \\|")
        void pipeEscaping() {
            List<List<String>> grid = List.of(
                    List.of("选项", "说明"),
                    List.of("A | B", "取值为 A 或 B")
            );

            String result = TableToMarkdown.convert(grid);

            // 管道符应被转义
            assertTrue(result.contains("A \\| B"));
            assertFalse(result.contains("A | B |")); // 不应出现原始管道符破坏表格结构
        }

        @Test
        @DisplayName("换行符替换 — 单元格内 \\n 替换为 <br>")
        void newlineReplacement() {
            List<List<String>> grid = List.of(
                    List.of("步骤", "内容"),
                    List.of("第一步", "打开浏览器\n输入地址")
            );

            String result = TableToMarkdown.convert(grid);

            // 单元格内的换行符已被替换为 <br>
            assertTrue(result.contains("打开浏览器<br>输入地址"),
                    "单元格内换行应转为 <br>，实际: " + result);
            // 不应包含原始换行符在单元格内部（表格结构换行除外）
            assertFalse(result.contains("打开浏览器\n输入地址"),
                    "单元格内不应保留原始 \\n");
        }

        @Test
        @DisplayName("空单元格补齐 — 保持列对齐")
        void emptyCellPadding() {
            List<List<String>> grid = List.of(
                    List.of("A", "B", "C"),
                    List.of("1") // 缺少 B、C 列
            );

            String result = TableToMarkdown.convert(grid);

            // 应补齐为空字符串（每行 3 个 |）
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("|")) {
                    // 每行应有 3 个 | 分隔符 + 首尾各 1 个 = 共 4 个
                    long pipeCount = line.chars().filter(c -> c == '|').count();
                    assertEquals(4, pipeCount, "行应保持列对齐: " + line);
                }
            }
        }
    }

    @Nested
    @DisplayName("批量转换")
    class BatchConversion {

        @Test
        @DisplayName("convertAll — 多表拼接，空表过滤")
        void convertAll() {
            List<List<List<String>>> tables = List.of(
                    List.of(
                            List.of("表1-头"),
                            List.of("表1-数据")
                    ),
                    List.of(), // 空表 — 应被过滤
                    List.of(
                            List.of("表2-头"),
                            List.of("表2-数据")
                    )
            );

            String result = TableToMarkdown.convertAll(tables);

            assertTrue(result.contains("表1-头"));
            assertTrue(result.contains("表2-头"));
            // 两张表之间应有空行分隔
            assertTrue(result.contains("\n\n"));
        }

        @Test
        @DisplayName("convertAll — 全部空表 → 空字符串")
        void allEmpty() {
            assertEquals("", TableToMarkdown.convertAll(List.of()));
            assertEquals("", TableToMarkdown.convertAll(null));
        }
    }

    @Nested
    @DisplayName("CJK 字符宽度")
    class CjkWidth {

        @Test
        @DisplayName("中英文混排 — 标题列按显示宽度对齐")
        void mixedLanguages() {
            List<List<String>> grid = List.of(
                    List.of("序号", "配置项名称", "默认值"),
                    List.of("1", "最大连接数", "100"),
                    List.of("2", "超时时间(秒)", "30")
            );

            String result = TableToMarkdown.convert(grid);

            // 中文列标题应比英文内容宽
            String[] lines = result.split("\n");
            // 分隔行
            assertTrue(lines[1].contains("---"));
            // 表头分隔行中 "配置项名称" 列应较宽（中文字符占 2 个显示位）
            String separator = lines[1];
            assertTrue(separator.length() > 15, "分隔行应足够宽以容纳中文: " + separator);
        }
    }
}
