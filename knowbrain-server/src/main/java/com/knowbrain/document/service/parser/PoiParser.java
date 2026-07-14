package com.knowbrain.document.service.parser;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.document.service.table.TableToMarkdown;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * POI 原生解析器 — Office 格式（docx / xlsx / pptx）→ Markdown
 *
 * <p>使用 Apache POI 5.3 原生 API 按结构提取内容并转为 Markdown：
 * <ul>
 *   <li><b>docx</b>：XWPFDocument → 段落（含标题检测）+ 表格</li>
 *   <li><b>xlsx</b>：Workbook → Sheet → Row → Cell（DataFormatter 显示值）</li>
 *   <li><b>pptx</b>：XMLSlideShow → Slide → Shape（按位置排序近似阅读顺序）</li>
 * </ul>
 *
 * <p>优先级 90，介于 Qwen-VL（100）和 Tika（10）之间。
 * 当 Office 文档不需要视觉理解（如 SmartArt/文本框）时使用此解析器。
 */
@Slf4j
@Component
public class PoiParser implements DocumentParser {

    private static final Set<String> SUPPORTED = Set.of("docx", "xlsx", "pptx");

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED;
    }

    @Override
    public ParsedDocument parse(byte[] fileBytes, String fileName) {
        long start = System.currentTimeMillis();
        String ext = extractExtension(fileName);

        try {
            return switch (ext) {
                case "docx" -> parseDocx(fileBytes, start);
                case "xlsx" -> parseXlsx(fileBytes, start);
                case "pptx" -> parsePptx(fileBytes, start);
                default -> throw new BizException(400, "POI 不支持的格式: ." + ext);
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("POI 解析异常: fileName={}", fileName, e);
            throw new BizException(500, "Office 文档解析失败，请检查文件格式");
        }
    }

    @Override
    public int priority() {
        return 90;
    }

    // ==================== docx ====================

    private ParsedDocument parseDocx(byte[] fileBytes, long start) throws Exception {
        List<String> tables = new ArrayList<>();
        StringBuilder md = new StringBuilder();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            for (IBodyElement element : doc.getBodyElements()) {
                switch (element.getElementType()) {
                    case PARAGRAPH -> {
                        String paragraphMd = convertParagraph((XWPFParagraph) element);
                        if (!paragraphMd.isEmpty()) {
                            md.append(paragraphMd).append("\n\n");
                        }
                    }
                    case TABLE -> {
                        String tableMd = convertDocxTable((XWPFTable) element);
                        if (!tableMd.isEmpty()) {
                            tables.add(tableMd);
                            md.append(tableMd).append("\n\n");
                        }
                    }
                    // CONTENTCONTROL — 跳过（结构化文档标签，内容已在 PARAGRAPH 中）
                    default -> {}
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("POI(docx) 解析完成: {} 字符, {} 张表格, {}ms",
                md.length(), tables.size(), elapsed);
        return new ParsedDocument(md.toString().trim(), tables, 1, ParseMetadata.of("poi", elapsed));
    }

    /** 段落 → Markdown（检测标题样式） */
    private String convertParagraph(XWPFParagraph para) {
        String text = para.getText();
        if (text == null || text.isBlank()) {
            return "";
        }
        text = text.trim();

        // 检测标题样式
        String styleName = para.getStyle();
        if (styleName != null) {
            String lower = styleName.toLowerCase();
            if (lower.contains("heading")) {
                // 提取标题级别
                int level = 1;
                for (int i = 1; i <= 6; i++) {
                    if (lower.contains("heading" + i) || lower.contains("heading " + i)) {
                        level = i;
                        break;
                    }
                }
                // 也检查 outline level（如 Word 中自定义的大纲级别）
                if (level == 1 && para.getCTP() != null && para.getCTP().getPPr() != null) {
                    var outlineLvl = para.getCTP().getPPr().getOutlineLvl();
                    if (outlineLvl != null && outlineLvl.getVal() != null) {
                        level = Math.min(outlineLvl.getVal().intValue() + 1, 6);
                    }
                }
                return "#".repeat(Math.min(level, 6)) + " " + text;
            }
        }

        // 加粗短文本 → 可能是子标题
        if (text.length() < 80 && hasBoldRun(para)) {
            String firstRunText = para.getRuns().isEmpty() ? "" :
                    para.getRuns().get(0).getText(0);
            if (firstRunText != null && firstRunText.trim().equals(text) && !text.matches(".*[。！？；，]$")) {
                return "**" + text + "**";
            }
        }

        return text;
    }

    /** 检测段落是否以加粗开头（子标题启发式检测） */
    private boolean hasBoldRun(XWPFParagraph para) {
        return para.getRuns().stream()
                .anyMatch(run -> run.isBold() && run.getText(0) != null && !run.getText(0).isBlank());
    }

    /** docx 表格 → 二维网格 → Markdown */
    private String convertDocxTable(XWPFTable table) {
        List<List<String>> grid = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> rowCells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                rowCells.add(cell.getText().trim());
            }
            if (!rowCells.isEmpty()) {
                grid.add(rowCells);
            }
        }
        return TableToMarkdown.convert(grid);
    }

    // ==================== xlsx ====================

    private ParsedDocument parseXlsx(byte[] fileBytes, long start) throws Exception {
        List<String> tables = new ArrayList<>();
        StringBuilder md = new StringBuilder();
        DataFormatter formatter = new DataFormatter();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            int sheetCount = wb.getNumberOfSheets();

            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = wb.getSheetAt(i);
                String sheetName = wb.getSheetName(i);

                List<List<String>> grid = extractSheetGrid(sheet, formatter);
                if (grid.isEmpty()) {
                    log.debug("xlsx Sheet '{}' 为空，跳过", sheetName);
                    continue;
                }

                // 输出标题：单 Sheet 不重复名称，多 Sheet 加名称区分
                if (sheetCount > 1) {
                    md.append("## ").append(sheetName).append("\n\n");
                } else if (!grid.isEmpty() && grid.get(0).size() > 1) {
                    // 单 Sheet 有表格数据才加轻标记
                    md.append("### ").append(sheetName).append("\n\n");
                }

                String tableMd = TableToMarkdown.convert(grid);
                tables.add(tableMd);
                md.append(tableMd).append("\n\n");
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("POI(xlsx) 解析完成: {} 字符, {} 张表格, {}ms",
                md.length(), tables.size(), elapsed);
        return new ParsedDocument(md.toString().trim(), tables, 1, ParseMetadata.of("poi", elapsed));
    }

    /** 提取 Sheet 的全部数据为二维网格（含合并单元格填充） */
    private List<List<String>> extractSheetGrid(Sheet sheet, DataFormatter formatter) {
        // 收集合并区域信息
        Map<String, String> mergedValues = new LinkedHashMap<>();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            String value = getCellDisplayValue(sheet, region.getFirstRow(), region.getFirstColumn(), formatter);
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    mergedValues.put(r + "," + c, value);
                }
            }
        }

        List<List<String>> grid = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        if (lastRow < 0) return grid;

        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                // 空行但在合并区域中 → 可能有合并单元格跨行值
                if (mergedValues.containsKey(r + ",0")) {
                    List<String> rowCells = new ArrayList<>();
                    rowCells.add(mergedValues.getOrDefault(r + ",0", ""));
                    grid.add(rowCells);
                }
                continue;
            }

            List<String> rowCells = new ArrayList<>();
            int lastCol = row.getLastCellNum();
            for (int c = 0; c < lastCol; c++) {
                String key = r + "," + c;
                if (mergedValues.containsKey(key)) {
                    rowCells.add(mergedValues.get(key));
                } else {
                    rowCells.add(getCellDisplayValue(row.getCell(c), formatter));
                }
            }
            // 去除行尾空单元格
            while (!rowCells.isEmpty() && rowCells.get(rowCells.size() - 1).isEmpty()) {
                rowCells.remove(rowCells.size() - 1);
            }
            if (!rowCells.isEmpty()) {
                grid.add(rowCells);
            }
        }

        // 去除末行全是空白的情况
        while (!grid.isEmpty() && grid.get(grid.size() - 1).stream().allMatch(String::isEmpty)) {
            grid.remove(grid.size() - 1);
        }

        return grid;
    }

    private String getCellDisplayValue(Sheet sheet, int row, int col, DataFormatter formatter) {
        Row r = sheet.getRow(row);
        if (r == null) return "";
        return getCellDisplayValue(r.getCell(col), formatter);
    }

    private String getCellDisplayValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        try {
            return formatter.formatCellValue(cell).trim();
        } catch (Exception e) {
            // DataFormatter 偶有格式异常，回退到 raw string
            try {
                return cell.getStringCellValue().trim();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    // ==================== pptx ====================

    private ParsedDocument parsePptx(byte[] fileBytes, long start) throws Exception {
        List<String> tables = new ArrayList<>();
        StringBuilder md = new StringBuilder();
        int slideCount = 0;

        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(fileBytes))) {
            List<XSLFSlide> slides = ppt.getSlides();

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                slideCount++;

                // 多页才加分隔标记
                if (slides.size() > 1) {
                    md.append("---\n");
                    md.append("**幻灯片 ").append(i + 1).append("**\n\n");
                }

                // 扁平化所有形状（递归展开 GroupShape → SmartArt 内容不丢失）
                List<XSLFShape> shapes = flattenAndSortShapes(slide);
                for (XSLFShape shape : shapes) {
                    if (shape instanceof XSLFTable pptTable) {
                        String tableMd = convertPptxTable(pptTable);
                        if (!tableMd.isEmpty()) {
                            tables.add(tableMd);
                            md.append(tableMd).append("\n\n");
                        }
                    } else if (shape instanceof XSLFTextShape textShape) {
                        String text = extractTextShape(textShape);
                        if (!text.isEmpty()) {
                            md.append(text).append("\n\n");
                        }
                    }
                    // XSLFPictureShape 跳过（无文本可提取）
                }

                // 提取演讲者备注（实质内容常在此）
                String notes = extractSlideNotes(slide);
                if (!notes.isEmpty()) {
                    md.append("> **备注**: ").append(notes).append("\n\n");
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("POI(pptx) 解析完成: {} 页, {} 字符, {} 张表格, {}ms",
                slideCount, md.length(), tables.size(), elapsed);
        return new ParsedDocument(md.toString().trim(), tables, slideCount, ParseMetadata.of("poi", elapsed));
    }

    /**
     * 扁平化幻灯片形状并按阅读顺序排序
     *
     * <p>处理要点：
     * <ul>
     *   <li>递归展开 {@link XSLFGroupShape}（SmartArt 本质上就是 GroupShape）</li>
     *   <li>Y-band 排序：同一水平带内（±50px）按 X 排序，跨带按 Y 排序</li>
     * </ul>
     */
    private List<XSLFShape> flattenAndSortShapes(XSLFSlide slide) {
        List<XSLFShape> flat = new ArrayList<>();
        for (XSLFShape shape : slide.getShapes()) {
            collectTextShapes(shape, flat);
        }

        // Y-band 排序：50px 容差内视为同一行
        flat.sort(Comparator
                .<XSLFShape>comparingDouble(s -> {
                    Rectangle2D a = s.getAnchor();
                    return a != null ? Math.floor(a.getY() / 50) * 50 : 0;
                })
                .thenComparingDouble(s -> {
                    Rectangle2D a = s.getAnchor();
                    return a != null ? a.getX() : 0;
                }));

        return flat;
    }

    /** 递归收集所有含文本的形状（深入 GroupShape） */
    private void collectTextShapes(XSLFShape shape, List<XSLFShape> collector) {
        if (shape instanceof XSLFGroupShape group) {
            // SmartArt / 组合形状 → 递归展开子形状
            for (XSLFShape child : group.getShapes()) {
                collectTextShapes(child, collector);
            }
        } else if (shape instanceof XSLFTable || shape instanceof XSLFTextShape) {
            collector.add(shape);
        }
        // XSLFPictureShape、XSLFConnectorShape 等无文本形状跳过
    }

    /** 提取幻灯片演讲者备注 */
    private String extractSlideNotes(XSLFSlide slide) {
        try {
            XSLFNotes notes = slide.getNotes();
            if (notes == null) return "";

            StringBuilder sb = new StringBuilder();
            for (XSLFShape shape : notes.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    String text = textShape.getText();
                    if (text != null && !text.isBlank()) {
                        sb.append(text.trim()).append("\n");
                    }
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("无法提取幻灯片备注: slide={}", slide.getSlideNumber(), e);
            return "";
        }
    }

    /** 文本形状 → 段落文本 */
    private String extractTextShape(XSLFTextShape shape) {
        String text = shape.getText();
        if (text == null || text.isBlank()) {
            return "";
        }
        // 用换行符保留段落间结构
        return text.trim();
    }

    /** pptx 表格 → 二维网格 → Markdown */
    private String convertPptxTable(XSLFTable table) {
        List<List<String>> grid = new ArrayList<>();
        for (XSLFTableRow row : table.getRows()) {
            List<String> rowCells = new ArrayList<>();
            for (XSLFTableCell cell : row.getCells()) {
                rowCells.add(cell.getText().trim());
            }
            if (!rowCells.isEmpty()) {
                grid.add(rowCells);
            }
        }
        return TableToMarkdown.convert(grid);
    }

    // ==================== 工具方法 ====================

    private String extractExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }
}
