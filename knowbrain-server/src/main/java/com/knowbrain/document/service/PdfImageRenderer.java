package com.knowbrain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 页面渲染器 — 将 PDF 逐页渲染为 PNG 图片，供 Qwen-VL OCR 使用
 */
@Slf4j
public class PdfImageRenderer {

    private static final int DPI = 200;           // 分辨率（200 DPI 平衡清晰度和文件大小）
    private static final int MAX_PAGES = 50;      // 单次最大处理页数
    private static final int MAX_IMAGE_WIDTH = 2048; // 图片最大宽度（限制 API 传输大小）

    /**
     * 将 PDF 字节数组逐页渲染为 PNG 图片
     *
     * @param pdfBytes PDF 文件字节数组
     * @return 每页一张 PNG 图片的字节数组列表
     */
    public List<byte[]> renderPages(byte[] pdfBytes) throws IOException {
        List<byte[]> images = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
            int totalPages = document.getNumberOfPages();
            int pages = Math.min(totalPages, MAX_PAGES);

            if (totalPages > MAX_PAGES) {
                log.warn("PDF 共 {} 页，仅处理前 {} 页", totalPages, MAX_PAGES);
            }

            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImage(i, DPI / 72f);

                // 图片过大时缩放
                if (image.getWidth() > MAX_IMAGE_WIDTH) {
                    image = resize(image, MAX_IMAGE_WIDTH);
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", bos);
                images.add(bos.toByteArray());
            }
        }

        log.info("PDF 渲染完成: {} 页 / {} dpi", images.size(), DPI);
        return images;
    }

    /** 等比例缩放，保持宽高比 */
    private BufferedImage resize(BufferedImage original, int maxWidth) {
        double ratio = (double) maxWidth / original.getWidth();
        int newHeight = (int) (original.getHeight() * ratio);
        BufferedImage scaled = new BufferedImage(maxWidth, newHeight, original.getType());
        java.awt.Graphics2D g = scaled.createGraphics();
        g.drawImage(original.getScaledInstance(maxWidth, newHeight, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return scaled;
    }
}
