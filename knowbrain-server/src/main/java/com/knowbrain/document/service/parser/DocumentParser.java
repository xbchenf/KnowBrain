package com.knowbrain.document.service.parser;

import java.util.Set;

/**
 * 文档解析器接口 — 策略模式核心抽象
 *
 * <p>每种文件格式对应一个实现：
 * <ul>
 *   <li>{@code QwenVlParser} — PDF（Qwen-VL 视觉模型 → Markdown）</li>
 *   <li>{@code PoiParser} — Office（docx/xlsx/pptx → Markdown）</li>
 *   <li>{@code TikaParser} — 兜底（Apache Tika 纯文本提取）</li>
 * </ul>
 *
 * <p>路由规则：{@code ParserRouter} 按 fileType 选择优先级最高的解析器。
 * 新增解析器只需实现此接口并注册到 Router — 无需修改调用方代码。
 *
 * @see ParsedDocument
 * @see ParseMetadata
 */
public interface DocumentParser {

    /**
     * 支持的 fileType 集合（小写，无点号前缀）
     *
     * <p>示例：{@code Set.of("pdf")}、{@code Set.of("docx", "xlsx", "pptx")}
     */
    Set<String> supportedExtensions();

    /**
     * 解析文件字节为结构化文档
     *
     * @param fileBytes 文件完整字节数组
     * @param fileName  原始文件名（用于判断子类型，如 .xlsx vs .xlsm）
     * @return 解析结果（Markdown + 表格 + 元数据）
     */
    ParsedDocument parse(byte[] fileBytes, String fileName);

    /**
     * 解析器优先级 — 值越大越优先
     *
     * <p>当同一 fileType 有多个解析器可用时（如 pdf 可走 Qwen-VL 或 Tika），
     * Router 选择优先级最高的。默认优先级参考：
     * <ul>
     *   <li>Qwen-VL（AI 引擎）：100（最高，需 API Key）</li>
     *   <li>POI（原生解析）：90</li>
     *   <li>Tika（通用兜底）：10（最低，保证总有解析器可用）</li>
     * </ul>
     */
    int priority();
}
