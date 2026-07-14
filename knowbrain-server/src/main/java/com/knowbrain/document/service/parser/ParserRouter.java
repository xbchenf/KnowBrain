package com.knowbrain.document.service.parser;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 解析器路由器 — 按 fileType + 优先级选择解析器，故障自动降级
 *
 * <p>核心职责：
 * <ol>
 *   <li>收集所有 {@link DocumentParser} 实现（Spring 自动注入）</li>
 *   <li>按 fileType 筛选 → 按 {@link DocumentParser#priority()} 降序排列</li>
 *   <li>依次尝试解析，失败时自动降级到下一个解析器</li>
 *   <li>所有解析器均失败时抛出异常</li>
 * </ol>
 *
 * <p>降级链路示例（PDF）：
 * <pre>
 * QwenVlParser(100) → 成功 ✅
 *                   → 失败（API Key 缺失/超时）→ TikaParser(10) → 成功 ✅
 * </pre>
 *
 * <p>扩展点：新增解析器只需实现 {@link DocumentParser} 并注册为 Spring Bean，
 * Router 自动发现，无需修改调用方代码。
 */
@Slf4j
@Component
public class ParserRouter {

    private final List<DocumentParser> parsers;

    /**
     * Spring 自动注入所有 {@link DocumentParser} 实现类
     */
    public ParserRouter(List<DocumentParser> parsers) {
        this.parsers = parsers;
        log.info("ParserRouter 初始化: 发现 {} 个解析器 — {}",
                parsers.size(),
                parsers.stream()
                        .map(p -> p.getClass().getSimpleName() + "(p=" + p.priority() + ")")
                        .toList());
    }

    /**
     * 解析文件 → 自动选择最优解析器，失败降级
     *
     * @param fileBytes 文件字节数组
     * @param fileName  原始文件名（用于判断 fileType）
     * @return 解析结果（Markdown + 元数据）
     * @throws BizException 所有解析器均失败时抛出
     */
    public ParsedDocument parse(byte[] fileBytes, String fileName) {
        String fileType = extractExtension(fileName);

        // 按优先级降序排列候选解析器
        List<DocumentParser> candidates = parsers.stream()
                .filter(p -> p.supportedExtensions().contains(fileType))
                .sorted(Comparator.comparingInt(DocumentParser::priority).reversed())
                .toList();

        if (candidates.isEmpty()) {
            throw new BizException(400,
                    "不支持的文件类型（." + fileType + "），请上传 pdf / docx / xlsx / pptx / txt / md 格式");
        }

        log.debug("文件 {} ({}) 候选解析器: {}", fileName, fileType,
                candidates.stream().map(p -> p.getClass().getSimpleName()).toList());

        // 依次尝试，失败降级
        Exception lastException = null;
        for (DocumentParser parser : candidates) {
            try {
                ParsedDocument result = parser.parse(fileBytes, fileName);
                // 检查结果有效性：解析器可能"成功"但返回空内容
                //   （如 Qwen-VL API Key 缺失时 extractText 返回 ""，不抛异常）
                if (!result.isValid()) {
                    log.warn("解析器 {} 返回空内容，降级到下一个 (fileType={})",
                            parser.getClass().getSimpleName(), fileType);
                    lastException = new BizException(500,
                            parser.getClass().getSimpleName() + " 返回空内容");
                    continue;
                }
                log.info("解析完成: fileType={}, parser={}, {} 字符, {}ms",
                        fileType, parser.getClass().getSimpleName(),
                        result.markdown().length(), result.metadata().elapsedMs());
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("解析器 {} 失败，降级到下一个 (fileType={}): {}",
                        parser.getClass().getSimpleName(), fileType, e.getMessage());
            }
        }

        log.error("所有解析器均失败: fileType={}, fileName={}, lastError={}",
                fileType, fileName,
                lastException != null ? lastException.getMessage() : "unknown");
        throw new BizException(500, "文档解析失败，不支持的文件格式或文件已损坏");
    }

    /** 从文件名提取小写扩展名（无点号前缀） */
    private String extractExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }
}
