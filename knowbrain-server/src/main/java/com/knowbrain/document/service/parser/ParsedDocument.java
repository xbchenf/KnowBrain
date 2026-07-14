package com.knowbrain.document.service.parser;

import java.util.Collections;
import java.util.List;

/**
 * 文档解析结果 — 解析器统一输出格式
 *
 * <p>所有解析器（Tika / POI / Qwen-VL）统一输出此结构，
 * 下游 ElementAwareChunker 按 Markdown 结构边界做原子化切片。
 *
 * @param markdown  全文 Markdown（含表格、标题、列表等结构化标记）
 * @param tables    独立表格列表（每条为 Markdown 表格格式：| col | col |）
 * @param pageCount 页数（PDF/PPTX 有意义，TXT/MD 为 1）
 * @param metadata  解析元数据（引擎、耗时、置信度）
 */
public record ParsedDocument(
        String markdown,
        List<String> tables,
        int pageCount,
        ParseMetadata metadata
) {

    /** 紧凑构造：无独立表格时使用 */
    public ParsedDocument(String markdown, int pageCount, ParseMetadata metadata) {
        this(markdown, Collections.emptyList(), pageCount, metadata);
    }

    /** 校验解析结果是否有效（内容非空） */
    public boolean isValid() {
        return markdown != null && !markdown.isBlank();
    }

    /** 是否含表格（用于判断 ElementAwareChunker 是否需要表格边界保护） */
    public boolean hasTables() {
        return tables != null && !tables.isEmpty();
    }
}
