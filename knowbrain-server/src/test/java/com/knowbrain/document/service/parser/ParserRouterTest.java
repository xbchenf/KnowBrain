package com.knowbrain.document.service.parser;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParserRouter — 解析器路由与降级")
class ParserRouterTest {

    private ParserRouter router;
    private StubParser highPriorityParser;
    private StubParser midPriorityParser;
    private StubParser lowPriorityParser;

    /** 桩解析器：可控返回或抛异常 */
    static class StubParser implements DocumentParser {
        private final String name;
        private final Set<String> extensions;
        private final int priority;
        private String result;
        private RuntimeException exception;

        StubParser(String name, Set<String> extensions, int priority) {
            this.name = name;
            this.extensions = extensions;
            this.priority = priority;
        }

        void setResult(String markdown) {
            this.result = markdown;
            this.exception = null;
        }

        void setException(RuntimeException e) {
            this.exception = e;
            this.result = null;
        }

        @Override
        public Set<String> supportedExtensions() { return extensions; }

        @Override
        public ParsedDocument parse(byte[] fileBytes, String fileName) {
            if (exception != null) throw exception;
            if (result != null) {
                return new ParsedDocument(result, 1, ParseMetadata.of(name, 0));
            }
            throw new BizException(500, "no result configured");
        }

        @Override
        public int priority() { return priority; }

        @Override
        public String toString() { return name + "(p=" + priority + ")"; }
    }

    @BeforeEach
    void setUp() {
        highPriorityParser = new StubParser("HighPrio", Set.of("pdf"), 100);
        midPriorityParser = new StubParser("MidPrio", Set.of("pdf", "docx"), 50);
        lowPriorityParser = new StubParser("LowPrio", Set.of("pdf", "docx", "txt"), 10);

        router = new ParserRouter(List.of(highPriorityParser, midPriorityParser, lowPriorityParser));
    }

    @Nested
    @DisplayName("优先级路由")
    class PriorityRouting {

        @Test
        @DisplayName("PDF → 选择优先级最高的 HighPrio(100)")
        void selectsHighestForPdf() {
            highPriorityParser.setResult("high quality markdown");
            midPriorityParser.setResult("medium quality");
            lowPriorityParser.setResult("low quality");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "test.pdf");

            assertEquals("high quality markdown", result.markdown());
            assertEquals("HighPrio", result.metadata().engine());
        }

        @Test
        @DisplayName("docx → 跳过 PDF-only 解析器，选择 MidPrio(50)")
        void skipsNonMatchingForDocx() {
            midPriorityParser.setResult("docx content");
            lowPriorityParser.setResult("low quality");

            // HighPrio 不支持 docx
            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "test.docx");

            assertEquals("docx content", result.markdown());
            assertEquals("MidPrio", result.metadata().engine());
        }

        @Test
        @DisplayName("txt → 只有 LowPrio(10) 支持")
        void onlyLowPrioForTxt() {
            lowPriorityParser.setResult("plain text");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "readme.txt");

            assertEquals("plain text", result.markdown());
            assertEquals("LowPrio", result.metadata().engine());
        }
    }

    @Nested
    @DisplayName("故障降级")
    class Fallback {

        @Test
        @DisplayName("PDF: HighPrio 失败 → 降级到 MidPrio")
        void fallbackFromHighToMid() {
            highPriorityParser.setException(new BizException(502, "API 不可用"));
            midPriorityParser.setResult("medium quality fallback");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "test.pdf");

            assertEquals("medium quality fallback", result.markdown());
            assertEquals("MidPrio", result.metadata().engine());
        }

        @Test
        @DisplayName("PDF: HighPrio + MidPrio 双失败 → 降级到 LowPrio")
        void fallbackThroughMultiple() {
            highPriorityParser.setException(new BizException(502, "API 超时"));
            midPriorityParser.setException(new BizException(500, "解析异常"));
            lowPriorityParser.setResult("last resort text");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "test.pdf");

            assertEquals("last resort text", result.markdown());
            assertEquals("LowPrio", result.metadata().engine());
        }

        @Test
        @DisplayName("全部失败 → 抛出 BizException")
        void allFailedThrows() {
            highPriorityParser.setException(new BizException(502, "Gone"));
            midPriorityParser.setException(new BizException(500, "Dead"));
            lowPriorityParser.setException(new BizException(500, "Gone too"));

            BizException ex = assertThrows(BizException.class, () ->
                    router.parse(new byte[]{1, 2, 3}, "test.pdf"));

            assertTrue(ex.getMessage().contains("不支持的文件格式或文件已损坏"));
        }

        @Test
        @DisplayName("PDF: HighPrio 返回空内容 → 降级到 MidPrio")
        void fallbackOnEmptyResult() {
            highPriorityParser.setResult("");  // 空字符串 → isValid()=false
            midPriorityParser.setResult("medium quality fallback");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "test.pdf");

            assertEquals("medium quality fallback", result.markdown());
            assertEquals("MidPrio", result.metadata().engine());
        }

        @Test
        @DisplayName("PDF: HighPrio 返回空白 → 降级到 MidPrio")
        void fallbackOnBlankResult() {
            highPriorityParser.setResult("   \t\n  ");  // 纯空白 → isValid()=false
            midPriorityParser.setResult("valid content");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "test.pdf");

            assertEquals("valid content", result.markdown());
            assertEquals("MidPrio", result.metadata().engine());
        }
    }

    @Nested
    @DisplayName("不支持的格式")
    class UnsupportedFormat {

        @Test
        @DisplayName("无人支持的格式 → 抛出 BizException(400)")
        void noParserAvailable() {
            BizException ex = assertThrows(BizException.class, () ->
                    router.parse(new byte[]{1, 2, 3}, "data.bin"));

            assertTrue(ex.getMessage().contains("不支持的文件类型"));
            assertTrue(ex.getMessage().contains("bin"));
        }
    }

    @Nested
    @DisplayName("文件名处理")
    class FileNameHandling {

        @Test
        @DisplayName("大写扩展名 → 转小写后匹配")
        void caseInsensitive() {
            highPriorityParser.setResult("parsed PDF");

            ParsedDocument result = router.parse(new byte[]{1, 2, 3}, "REPORT.PDF");

            assertEquals("parsed PDF", result.markdown());
        }

        @Test
        @DisplayName("无扩展名 → 空字符串，无人匹配")
        void noExtension() {
            BizException ex = assertThrows(BizException.class, () ->
                    router.parse(new byte[]{1, 2, 3}, "Makefile"));

            assertTrue(ex.getMessage().contains("不支持的文件类型"));
        }
    }
}
