package com.knowbrain.retrieval.engine;

import com.knowbrain.scenario.FaqService;
import com.knowbrain.scenario.GlossaryService;
import com.knowbrain.security.SensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询预处理器
 *
 * 流水线：原始问题 → 术语改写 → 敏感词过滤 → FAQ 精确匹配 → 向量检索 → LLM 生成
 *
 * FAQ 命中时直接返回预设答案（短路），跳过向量检索和 LLM 调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryPreprocessor {

    private final GlossaryService glossaryService;
    private final FaqService faqService;
    private final SensitiveWordFilter sensitiveWordFilter;

    /**
     * 预处理结果
     */
    public static class Result {
        private final String rewrittenQuery;
        private final String faqAnswer;
        private final String faqQuestion;
        private final boolean faqMatched;

        public Result(String rewrittenQuery) {
            this.rewrittenQuery = rewrittenQuery;
            this.faqAnswer = null;
            this.faqQuestion = null;
            this.faqMatched = false;
        }

        public Result(String rewrittenQuery, String faqAnswer, String faqQuestion) {
            this.rewrittenQuery = rewrittenQuery;
            this.faqAnswer = faqAnswer;
            this.faqQuestion = faqQuestion;
            this.faqMatched = true;
        }

        public String rewrittenQuery() { return rewrittenQuery; }
        public String faqAnswer() { return faqAnswer; }
        public String faqQuestion() { return faqQuestion; }
        public boolean isFaqMatched() { return faqMatched; }
    }

    /**
     * 预处理用户查询
     */
    public Result preprocess(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return new Result(rawQuery);
        }

        String trimmed = rawQuery.trim();

        // 1. FAQ 精确匹配（在术语改写之前，避免改写破坏问题文本匹配）
        var faqResult = faqService.match(trimmed);
        if (faqResult != null) {
            String sanitized = sensitiveWordFilter.sanitize(trimmed);
            log.info("FAQ 命中: score={}, \"{}\"", faqResult.score(),
                    faqResult.entry().getQuestion());
            return new Result(sanitized, faqResult.entry().getAnswer(), faqResult.entry().getQuestion());
        }

        // 2. 术语映射：口语 → 正式术语
        String rewritten = glossaryService.rewrite(trimmed);

        // 3. 敏感词过滤
        rewritten = sensitiveWordFilter.sanitize(rewritten);

        // 4. 改写后再试一次 FAQ 匹配（术语改写后可能命中新关键词）
        if (!rewritten.equals(trimmed)) {
            faqResult = faqService.match(rewritten);
            if (faqResult != null) {
                log.info("FAQ 改写后命中: score={}, \"{}\"", faqResult.score(),
                        faqResult.entry().getQuestion());
                return new Result(rewritten, faqResult.entry().getAnswer(), faqResult.entry().getQuestion());
            }
            log.info("查询预处理完成: 查询已被改写");
        }
        return new Result(rewritten);
    }
}
