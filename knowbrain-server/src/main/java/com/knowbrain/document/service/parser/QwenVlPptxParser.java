package com.knowbrain.document.service.parser;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.document.service.QwenVisionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

/**
 * Qwen-VL 视觉增强 PPT 解析器 — PPT 幻灯片渲染 → Qwen-VL → Markdown
 *
 * <p>优先级 95（介于 QwenVlParser PDF=100 和 PoiParser=90 之间），
 * 是 PPT 文档的首选解析引擎。工作流程：
 * <ol>
 *   <li>POI 逐页渲染幻灯片为 2x 缩放 PNG 图片（含图表、SmartArt、图片中嵌入文字）</li>
 *   <li>逐页调用 Qwen-VL（qwen-vl-max）将页面转为 Markdown</li>
 *   <li>拼接所有页面为完整文档</li>
 * </ol>
 *
 * <p>与 {@link QwenVlParser} 是镜像设计——共享 {@link QwenVisionService}，
 * 区别仅在于渲染引擎（POI Slide.draw 替代 PDFBox renderPage）。
 *
 * <p>不可用时自动降级：API Key 未配置或 Qwen-VL API 不可达时，
 * {@code ParserRouter} 捕获异常 → {@link PoiParser}(90) → {@link TikaParser}(10)。
 */
@Slf4j
@Component
public class QwenVlPptxParser implements DocumentParser {

    private static final Set<String> SUPPORTED = Set.of("pptx");

    /** 2x 缩放：PPT 通常 960×540px，2x 后 1920×1080 保证图表细节清晰 */
    private static final double SCALE = 2.0;

    private final QwenVisionService visionService;

    public QwenVlPptxParser(QwenVisionService visionService) {
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

        if (!"pptx".equals(ext)) {
            throw new BizException(400, "Qwen-VL(PPT) 仅支持 pptx 格式，收到: ." + ext);
        }

        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(fileBytes))) {
            List<XSLFSlide> slides = ppt.getSlides();
            if (slides.isEmpty()) {
                throw new BizException(500, "PPT 无幻灯片（文件可能损坏或为空）");
            }

            log.info("Qwen-VL(PPT) 解析开始: {} 页, fileName={}", slides.size(), fileName);
            StringBuilder markdown = new StringBuilder();

            for (int i = 0; i < slides.size(); i++) {
                long pageStart = System.currentTimeMillis();

                // Step 1: 渲染整页幻灯片为图片（含图表、SmartArt、图片文字）
                byte[] imageBytes = renderSlide(slides.get(i));

                // Step 2: Qwen-VL 图片 → Markdown（复用已有服务）
                String pageMd = visionService.extractText(imageBytes, i + 1);
                long pageElapsed = System.currentTimeMillis() - pageStart;

                if (!pageMd.isBlank()) {
                    if (i > 0 && slides.size() > 1) {
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
            log.info("Qwen-VL(PPT) 解析完成: {} 页, {} 字符, {}ms",
                    slides.size(), fullMarkdown.length(), elapsed);

            double confidence = fullMarkdown.isEmpty() ? 0.0 : 0.95;

            return new ParsedDocument(
                    fullMarkdown,
                    slides.size(),
                    ParseMetadata.of("qwen-vl-ppt", elapsed, confidence)
            );

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Qwen-VL(PPT) 解析异常: fileName={}", fileName, e);
            throw new BizException(500, "PPT 解析失败，请检查文件格式或稍后重试");
        }
    }

    @Override
    public int priority() {
        return 95; // 介于 QwenVlParser PDF(100) 和 PoiParser(90) 之间
    }

    // ---- 渲染 ----

    /**
     * 将单页幻灯片渲染为 PNG 图片（2x 缩放，白底）
     *
     * <p>使用 {@link XSLFSlide#draw(Graphics2D)} 渲染整页：
     * 文本、表格、SmartArt、图表、嵌入图片 — 所有视觉元素一网打尽。
     */
    private byte[] renderSlide(XSLFSlide slide) {
        try {
            Dimension pageSize = slide.getSlideShow().getPageSize();
            int width = (int) (pageSize.getWidth() * SCALE);
            int height = (int) (pageSize.getHeight() * SCALE);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            // 渲染质量设置
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 白色背景（避免透明区域在 Qwen-VL 中显示异常）
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // 缩放 + 渲染
            g.scale(SCALE, SCALE);
            slide.draw(g);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            return bos.toByteArray();

        } catch (Exception e) {
            log.error("PPT 幻灯片渲染失败: slide={}", slide.getSlideNumber(), e);
            throw new BizException(500, "幻灯片渲染失败，请检查文件完整性");
        }
    }

    private String extractExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }
}
