package com.knowbrain.document.service.parser;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.document.service.PdfImageRenderer;
import com.knowbrain.document.service.QwenVisionService;
import com.knowbrain.document.service.table.TableToMarkdown;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Qwen-VL 视觉模型解析器 — PDF 页面 → Markdown（表格/标题/列表结构保留）
 *
 * <p>优先级最高（100），是 PDF 文档的首选解析引擎。工作流程：
 * <ol>
 *   <li>PDFBox 渲染每页为 200 DPI PNG 图片</li>
 *   <li>逐页调用 Qwen-VL（qwen-vl-max）将页面转为 Markdown</li>
 *   <li>拼接所有页面为完整文档</li>
 * </ol>
 *
 * <p>适用场景：
 * <ul>
 *   <li>文本型 PDF（制度手册、项目报告）</li>
 *   <li>扫描件 PDF（盖章合同、老文件扫描）</li>
 *   <li>含表格/图表的复杂排版 PDF</li>
 * </ul>
 *
 * <p>不可用时自动降级：当 API Key 未配置或 Qwen-VL API 不可达时，
 * {@code ParserRouter} 会捕获异常并切换为 {@link TikaParser}。
 */
@Slf4j
@Component
public class QwenVlParser implements DocumentParser {

    private static final Set<String> SUPPORTED = Set.of("pdf");

    private final QwenVisionService visionService;

    @Value("${qwen-vl.max-pages:50}")
    private int maxPages;

    public QwenVlParser(QwenVisionService visionService) {
        this.visionService = visionService;
    }

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED;
    }

    @Override
    public ParsedDocument parse(byte[] fileBytes, String fileName) {
        long start = System.currentTimeMillis();
        String ext = extractExtension(fileName);

        if (!"pdf".equals(ext)) {
            throw new BizException(400, "Qwen-VL 仅支持 PDF 格式，收到: ." + ext);
        }

        try {
            PdfImageRenderer renderer = new PdfImageRenderer();
            List<byte[]> pageImages = renderer.renderPages(fileBytes);

            if (pageImages.isEmpty()) {
                throw new BizException(500, "PDF 渲染无页面（文件可能损坏或为空）");
            }

            log.info("Qwen-VL 解析开始: {} 页, fileName={}", pageImages.size(), fileName);
            StringBuilder markdown = new StringBuilder();

            for (int i = 0; i < pageImages.size(); i++) {
                long pageStart = System.currentTimeMillis();
                String pageMd = visionService.extractText(pageImages.get(i), i + 1);
                long pageElapsed = System.currentTimeMillis() - pageStart;

                if (!pageMd.isBlank()) {
                    // 多页文档加页间分隔
                    if (i > 0 && pageImages.size() > 1) {
                        markdown.append("\n---\n\n");
                    }
                    markdown.append(pageMd).append("\n");
                    log.debug("  第 {} 页完成: {} 字符, {}ms", i + 1, pageMd.length(), pageElapsed);
                } else {
                    log.warn("  第 {} 页提取为空: {}ms", i + 1, pageElapsed);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            String fullMarkdown = markdown.toString().trim();
            log.info("Qwen-VL 解析完成: {} 页, {} 字符, {}ms",
                    pageImages.size(), fullMarkdown.length(), elapsed);

            // Qwen-VL 置信度：高质量视觉模型，但复杂排版可能有偏差
            double confidence = fullMarkdown.isEmpty() ? 0.0 : 0.95;
            List<String> tables = TableToMarkdown.extractTables(fullMarkdown);

            return new ParsedDocument(
                    fullMarkdown,
                    tables,
                    pageImages.size(),
                    ParseMetadata.of("qwen-vl", elapsed, confidence)
            );

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Qwen-VL 解析异常: fileName={}", fileName, e);
            throw new BizException(500, "PDF 解析失败，请稍后重试");
        }
    }

    @Override
    public int priority() {
        return 100; // 最高优先级 — PDF 首选引擎
    }

    private String extractExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }
}
