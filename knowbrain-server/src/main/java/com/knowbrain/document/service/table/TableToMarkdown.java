package com.knowbrain.document.service.table;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * POI 表格 → Markdown 表格格式转换工具
 *
 * <p>将 Apache POI 提取的二维单元格网格转为 GFM（GitHub Flavored Markdown）表格：
 * <pre>{@code
 * | 列1 | 列2 | 列3 |
 * |-----|-----|-----|
 * | 值1 | 值2 | 值3 |
 * }</pre>
 *
 * <p>处理要点：
 * <ul>
 *   <li>合并单元格 → 自动填充重复值</li>
 *   <li>管道符转义 → 单元格内的 {@code |} 替换为 {@code \|}</li>
 *   <li>换行符处理 → 单元格内换行替换为 {@code <br>}</li>
 *   <li>空单元格 → 空字符串（保持列对齐）</li>
 * </ul>
 */
public class TableToMarkdown {

    private TableToMarkdown() {
        // 工具类，禁止实例化
    }

    /**
     * 将二维网格转为单张 Markdown 表格字符串
     *
     * @param grid 二维单元格网格（第一行视为表头）
     * @return Markdown 格式表格，网格为空时返回空字符串
     */
    public static String convert(List<List<String>> grid) {
        if (grid == null || grid.isEmpty()) {
            return "";
        }

        // 1. 计算每列最大宽度（用于对齐，提升源码可读性）
        int colCount = grid.stream().mapToInt(List::size).max().orElse(0);
        if (colCount == 0) {
            return "";
        }
        int[] widths = computeColumnWidths(grid, colCount);

        // 2. 规范化网格（补齐列数、转义特殊字符）
        List<List<String>> normalized = normalizeGrid(grid, colCount);

        // 3. 渲染
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < normalized.size(); r++) {
            List<String> row = normalized.get(r);
            sb.append("| ");
            for (int c = 0; c < colCount; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                sb.append(padRight(cell, widths[c]));
                sb.append(" | ");
            }
            sb.append("\n");

            // 表头分隔线（第一行之后）
            if (r == 0) {
                sb.append("|");
                for (int c = 0; c < colCount; c++) {
                    sb.append(" ").append("-".repeat(Math.max(3, widths[c]))).append(" |");
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * 批量转换 — 多张表格拼接（每张表之间空行分隔）
     */
    public static String convertAll(List<List<List<String>>> tables) {
        if (tables == null || tables.isEmpty()) {
            return "";
        }
        return tables.stream()
                .map(TableToMarkdown::convert)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    // ---- 内部方法 ----

    /** 计算每列的最大显示宽度 */
    private static int[] computeColumnWidths(List<List<String>> grid, int colCount) {
        int[] widths = new int[colCount];
        for (List<String> row : grid) {
            for (int c = 0; c < Math.min(row.size(), colCount); c++) {
                String cell = row.get(c);
                // 按显示长度（中文占 2 个字符宽）
                int displayLen = displayLength(cell != null ? cell : "");
                widths[c] = Math.max(widths[c], displayLen);
            }
        }
        // 最小宽度 3（匹配 GFM 表头分隔线最低要求）
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(widths[i], 3);
        }
        return widths;
    }

    /** 补齐列数 + 转义特殊字符 */
    private static List<List<String>> normalizeGrid(List<List<String>> grid, int colCount) {
        List<List<String>> result = new ArrayList<>(grid.size());
        for (List<String> row : grid) {
            List<String> normalized = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                if (cell == null) {
                    cell = "";
                }
                // 转义 Markdown 特殊字符 + 换行替换
                cell = cell.replace("|", "\\|")
                           .replace("\n", "<br>")
                           .replace("\r", "");
                normalized.add(cell);
            }
            result.add(normalized);
        }
        return result;
    }

    /** 计算字符串的显示宽度（ASCII 字符计 1，其他计 2） */
    private static int displayLength(String s) {
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            len += (ch <= 0x7F) ? 1 : 2;
        }
        return len;
    }

    /**
     * 从 Markdown 文本中提取所有表格块
     *
     * <p>识别连续的 {@code | ... |} 行（含表头分隔线），每段作为一个独立表格字符串。
     * 用于 Qwen-VL 等视觉模型输出的后处理：模型已将页面转为 Markdown，
     * 但未单独列出表格列表，此方法补全表格元数据。
     *
     * @param markdown 完整的 Markdown 文本
     * @return 独立表格块列表，每项为一个完整的 GFM 表格字符串
     */
    public static List<String> extractTables(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        // 匹配 Markdown 表格行：| ... |（允许首尾空白）
        Pattern tableRow = Pattern.compile("^\\s*\\|.+\\|\\s*$");

        List<String> tables = new ArrayList<>();
        String[] lines = markdown.split("\\R");

        int i = 0;
        while (i < lines.length) {
            // 寻找表格起始行（非分隔线的 |...| 行，且下一行是分隔线）
            if (tableRow.matcher(lines[i]).matches() && !isSeparatorLine(lines[i])) {
                // 检查下一行是否为分隔线
                if (i + 1 < lines.length && isSeparatorLine(lines[i + 1])) {
                    int start = i;
                    i += 2; // 跳过表头 + 分隔线

                    // 收集后续数据行
                    while (i < lines.length && tableRow.matcher(lines[i]).matches()) {
                        i++;
                    }

                    // 提取表格块
                    StringBuilder tableBlock = new StringBuilder();
                    for (int j = start; j < i; j++) {
                        if (j > start) tableBlock.append("\n");
                        tableBlock.append(lines[j].trim());
                    }
                    String table = tableBlock.toString().trim();
                    if (!table.isEmpty()) {
                        tables.add(table);
                    }
                    continue;
                }
            }
            i++;
        }

        return tables;
    }

    /** 检查是否为 Markdown 表格分隔线（如 |---|:---:|---|） */
    private static boolean isSeparatorLine(String line) {
        return line.matches("^\\s*\\|[\\s:-]+\\|\\s*$") && line.contains("-");
    }

    /** 右侧填充空格至指定显示宽度 */
    private static String padRight(String s, int targetDisplayWidth) {
        int current = displayLength(s);
        if (current >= targetDisplayWidth) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < targetDisplayWidth - current; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
