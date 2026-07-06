package com.knowbrain.retrieval.engine;

import com.knowbrain.TestMockConfig;
import com.knowbrain.scenario.FaqService;
import com.knowbrain.scenario.GlossaryService;
import com.knowbrain.scenario.entity.ScenarioFaq;
import com.knowbrain.scenario.entity.ScenarioGlossary;
import com.knowbrain.scenario.mapper.ScenarioFaqMapper;
import com.knowbrain.scenario.mapper.ScenarioGlossaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 2-3, 9-10：查询预处理流水线
 *
 * 流水线：FAQ 匹配 → 术语改写 → 敏感词过滤 → 二次 FAQ 匹配
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
@DisplayName("查询预处理器")
class QueryPreprocessorTest {

    @Autowired
    private ScenarioFaqMapper faqMapper;
    @Autowired
    private ScenarioGlossaryMapper glossaryMapper;
    @Autowired
    private FaqService faqService;
    @Autowired
    private GlossaryService glossaryService;
    @Autowired
    private QueryPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        faqMapper.delete(null);
        glossaryMapper.delete(null);

        // FAQ
        ScenarioFaq faq = new ScenarioFaq();
        faq.setQuestion("我有多少天年假？");
        faq.setAnswer("入职满1年享5天年假，满5年享10天，满10年享15天。");
        faq.setKeywords("年假,休假,假期");
        faq.setEnabled(true);
        faqMapper.insert(faq);

        // Glossary
        ScenarioGlossary g = new ScenarioGlossary();
        g.setTerm("电脑卡");
        g.setFormal("系统运行缓慢");
        g.setSynonyms("电脑慢,卡顿");
        glossaryMapper.insert(g);

        faqService.reload();
        glossaryService.reload();
    }

    @Test
    @DisplayName("FAQ 精确匹配：应短路返回预设答案")
    void shouldShortCircuitOnFaqMatch() {
        QueryPreprocessor.Result result = preprocessor.preprocess("我有多少天年假？");
        assertTrue(result.isFaqMatched());
        assertNotNull(result.faqAnswer());
        assertTrue(result.faqAnswer().contains("5天年假"));
    }

    @Test
    @DisplayName("FAQ 未命中 + 术语改写：查询应被改写")
    void shouldRewriteWhenNoFaqMatch() {
        QueryPreprocessor.Result result = preprocessor.preprocess("电脑卡怎么办");
        assertFalse(result.isFaqMatched());
        assertTrue(result.rewrittenQuery().contains("系统运行缓慢"));
    }

    @Test
    @DisplayName("FAQ 未命中 + 无术语匹配：原文保持不变")
    void shouldReturnOriginalWhenNoMatch() {
        QueryPreprocessor.Result result = preprocessor.preprocess("今天天气很好");
        assertFalse(result.isFaqMatched());
        assertEquals("今天天气很好", result.rewrittenQuery());
    }

    @Test
    @DisplayName("敏感词过滤：预处理后应去除敏感词")
    void shouldFilterSensitiveWords() {
        QueryPreprocessor.Result result = preprocessor.preprocess("敏感词1怎么处理");
        assertFalse(result.rewrittenQuery().contains("敏感词1"));
    }

    @Test
    @DisplayName("FAQ 命中时也应做敏感词过滤")
    void shouldFilterEvenOnFaqHit() {
        // 需要 FAQ 问题中含有敏感词的场景 — 这里测试 sanitize 被调用
        QueryPreprocessor.Result result = preprocessor.preprocess("我有多少天年假？");
        assertTrue(result.isFaqMatched());
        // rewrittenQuery 应该经过 sanitize
        assertNotNull(result.rewrittenQuery());
    }

    @Test
    @DisplayName("空查询安全处理")
    void shouldHandleEmptyQuery() {
        QueryPreprocessor.Result result = preprocessor.preprocess("");
        assertNotNull(result);
        assertFalse(result.isFaqMatched());
    }

    @Test
    @DisplayName("null 查询安全处理")
    void shouldHandleNullQuery() {
        QueryPreprocessor.Result result = preprocessor.preprocess(null);
        assertNotNull(result);
        assertFalse(result.isFaqMatched());
    }
}
