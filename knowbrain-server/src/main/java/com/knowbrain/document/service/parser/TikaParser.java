package com.knowbrain.document.service.parser;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Tika 通用解析器 — 基于 Apache Tika 文本提取，作为兜底解析引擎
 *
 * <p>优先级最低（10），当 Qwen-VL / POI 等专用解析器不可用时自动降级使用。
 * 支持常见文档格式的纯文本提取（不含表格结构保留）。
 *
 * <p>代码来源：从 {@code DocumentServiceImpl.parseWithTika()} 抽离，
 * 逻辑不变，仅适配 {@link DocumentParser} 接口。
 */
@Slf4j
@Component
public class TikaParser implements DocumentParser {

    /** Tika 稳定支持的文本格式（兜底时覆盖这些扩展名） */
    private static final Set<String> SUPPORTED = Set.of(
            "pdf", "docx", "doc", "txt", "md", "html", "rtf", "xml", "csv",
            "pptx", "ppt", "xlsx", "xls"
    );

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED;
    }

    @Override
    public ParsedDocument parse(byte[] fileBytes, String fileName) {
        long start = System.currentTimeMillis();
        Path tempFile = null;
        try {
            String suffix = extractSuffix(fileName);
            tempFile = Files.createTempFile("knowbrain-tika-", suffix);
            Files.write(tempFile, fileBytes);

            TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(tempFile.toFile()));
            String text = reader.get().stream()
                    .map(Document::getContent)
                    .reduce("", (a, b) -> a + "\n" + b);

            long elapsed = System.currentTimeMillis() - start;
            log.debug("Tika 解析完成: file={}, {} 字符, {}ms", fileName,
                    text != null ? text.length() : 0, elapsed);
            return new ParsedDocument(text, 1, ParseMetadata.of("tika", elapsed));
        } catch (Exception e) {
            throw new BizException(500, "文档解析失败，请检查文件格式或稍后重试");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public int priority() {
        return 10; // 最低优先级，兜底
    }

    /** 从文件名提取后缀（含点号），默认 .tmp */
    private String extractSuffix(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return ".tmp";
    }
}
