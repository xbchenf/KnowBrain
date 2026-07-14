package com.knowbrain.document.service.chunker;

import com.knowbrain.document.service.SmartChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 元素感知分块器 — Markdown 结构保护 + SmartChunker 段落级切分
 *
 * <p>核心原则：<b>原子元素不被跨 chunk 拆分</b>
 *
 * <p>分块策略（优先级从高到低）：
 * <ol>
 *   <li><b>表格</b>（|...| 格式）→ 独立 chunk，永不拆分（超长表格允许独占大 chunk）</li>
 *   <li><b>代码块</b>（``` 围栏）→ 独立 chunk，永不拆分</li>
 *   <li><b>标题行</b>（# 开头）→ 强制开始新段落，标题与其下正文保持同 chunk</li>
 *   <li><b>普通段落</b> → 委托 {@link SmartChunker} 按语义边界递归切分</li>
 * </ol>
 *
 * <p>参数：目标 350 字/片，重叠 80 字（与 SmartChunker 一致）
 */
@Slf4j
@Component
public class ElementAwareChunker {

    private static final int CHUNK_SIZE = 350;
    private static final int OVERLAP = 80;

    /** Markdown 表格行：以 | 开头、以 | 结尾 */
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|.*\\|$");

    /** Markdown 表格分隔行：| --- | --- | 等 */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|\\s*[-:]+\\s*\\|.*\\|$");

    /** 代码围栏：``` */
    private static final String FENCE = "```";

    private final SmartChunker smartChunker;

    public ElementAwareChunker(SmartChunker smartChunker) {
        this.smartChunker = smartChunker;
    }

    /**
     * 对 Markdown 文本做结构保护分块
     *
     * @param markdown 全文 Markdown（来自解析器输出）
     * @return 切片列表（每个切片 ≤ CHUNK_SIZE，除非含超长表格）
     */
    public List<String> chunk(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }

        List<String> blocks = splitIntoBlocks(markdown);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String overlapTail = "";

        for (String block : blocks) {
            BlockType type = classifyBlock(block);

            switch (type) {
                case TABLE, CODE_BLOCK -> {
                    // 原子块：先 flush 当前累积的段落
                    if (current.length() > 0) {
                        chunks.add(current.toString().trim());
                        overlapTail = takeLastChars(current.toString(), OVERLAP);
                        current = new StringBuilder();
                    }
                    // 表格/代码块独占 chunk
                    String trimmed = block.trim();
                    if (trimmed.length() <= CHUNK_SIZE * 2) {
                        chunks.add(trimmed);
                    } else {
                        // 超长表格：记录警告但保持完整
                        log.warn("超长原子块 ({} 字符): 保持完整不分拆", trimmed.length());
                        chunks.add(trimmed);
                    }
                }

                case HEADING -> {
                    // 标题强制开始新段落
                    if (current.length() > 0) {
                        chunks.add(current.toString().trim());
                        overlapTail = takeLastChars(current.toString(), OVERLAP);
                        current = new StringBuilder();
                    }
                    current.append(block).append("\n\n");
                }

                case PARAGRAPH -> {
                    // 普通段落：累积 → SmartChunker 切分
                    if (current.length() + block.length() > CHUNK_SIZE) {
                        if (current.length() > 0) {
                            // 用 SmartChunker 对累积文本做语义切分
                            List<String> subChunks = smartChunker.chunk(current.toString());
                            for (int i = 0; i < subChunks.size(); i++) {
                                String sc = subChunks.get(i);
                                // 首段加 overlap
                                if (i == 0 && !overlapTail.isEmpty() && chunks.size() > 0) {
                                    // overlap 已在上一 chunk 末尾，这里保持连贯
                                }
                                chunks.add(sc);
                            }
                            overlapTail = subChunks.isEmpty() ? "" :
                                    takeLastChars(subChunks.get(subChunks.size() - 1), OVERLAP);
                            current = new StringBuilder();
                        }
                    }
                    current.append(block).append("\n\n");
                }
            }
        }

        // 尾块处理
        if (current.length() > 0) {
            List<String> tailChunks = smartChunker.chunk(current.toString());
            chunks.addAll(tailChunks);
        }

        // 过滤空字符串
        chunks.removeIf(String::isBlank);

        log.debug("ElementAware 分块完成: {} 块 → {} chunks", blocks.size(), chunks.size());
        return chunks;
    }

    // ---- 内部方法 ----

    /** 将 Markdown 按结构边界拆分为逻辑块 */
    private List<String> splitIntoBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        boolean inCodeBlock = false;
        boolean inTable = false;

        for (String line : lines) {
            // 代码围栏切换
            if (line.trim().startsWith(FENCE)) {
                if (inCodeBlock) {
                    // 代码块结束
                    current.append(line).append("\n");
                    blocks.add(current.toString());
                    current = new StringBuilder();
                    inCodeBlock = false;
                } else {
                    // 代码块开始：先 flush 当前段落
                    if (current.length() > 0) {
                        blocks.add(current.toString());
                        current = new StringBuilder();
                    }
                    current.append(line).append("\n");
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                current.append(line).append("\n");
                continue;
            }

            // 表格行检测
            boolean isTableRow = TABLE_ROW.matcher(line.trim()).matches();
            boolean isTableSep = TABLE_SEPARATOR.matcher(line.trim()).matches();

            if (isTableRow || isTableSep) {
                if (!inTable) {
                    // 表格开始：flush 当前段落
                    if (current.length() > 0) {
                        blocks.add(current.toString());
                        current = new StringBuilder();
                    }
                    inTable = true;
                }
                current.append(line).append("\n");
            } else if (inTable && line.trim().isEmpty()) {
                // 空行结束表格
                current.append("\n");
                blocks.add(current.toString());
                current = new StringBuilder();
                inTable = false;
            } else if (inTable) {
                // 表格内的非表格行 → 可能是表格结束边界
                blocks.add(current.toString());
                current = new StringBuilder();
                current.append(line).append("\n");
                inTable = false;
            } else {
                current.append(line).append("\n");
            }
        }

        // 收尾
        if (current.length() > 0) {
            blocks.add(current.toString());
        }

        return blocks;
    }

    /** 对单个块分类 */
    private BlockType classifyBlock(String block) {
        String trimmed = block.trim();
        if (trimmed.isEmpty()) {
            return BlockType.PARAGRAPH;
        }
        // 代码围栏
        if (trimmed.startsWith(FENCE) && trimmed.endsWith(FENCE)) {
            return BlockType.CODE_BLOCK;
        }
        // 表格：检查是否以 | 开头且中间有分隔行
        if (trimmed.startsWith("|") && TABLE_SEPARATOR.matcher(trimmed).find()) {
            return BlockType.TABLE;
        }
        // 标题（# 开头但不是代码块）
        if (trimmed.startsWith("#")) {
            return BlockType.HEADING;
        }
        return BlockType.PARAGRAPH;
    }

    /** 取字符串末尾 n 个字符 */
    private String takeLastChars(String s, int n) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= n) return s;
        return s.substring(s.length() - n);
    }

    private enum BlockType {
        TABLE, CODE_BLOCK, HEADING, PARAGRAPH
    }
}
