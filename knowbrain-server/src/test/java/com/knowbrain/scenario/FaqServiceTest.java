package com.knowbrain.scenario;

import com.knowbrain.TestMockConfig;
import com.knowbrain.scenario.entity.ScenarioFaq;
import com.knowbrain.scenario.mapper.ScenarioFaqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 2-3：FAQ 精确匹配 + 关键词匹配
 */
@SpringBootTest
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DisplayName("FAQ 预设问答匹配")
class FaqServiceTest {

    @Autowired
    private ScenarioFaqMapper faqMapper;

    @Autowired
    private FaqService faqService;

    @BeforeEach
    void setUp() {
        // 清理旧数据
        faqMapper.delete(null);

        // 插入测试 FAQ 数据
        ScenarioFaq faq1 = new ScenarioFaq();
        faq1.setQuestion("我有多少天年假？");
        faq1.setAnswer("根据公司规定，入职满1年享5天年假，满5年享10天，满10年享15天。");
        faq1.setKeywords("年假,休假,假期");
        faq1.setEnabled(true);
        faqMapper.insert(faq1);

        ScenarioFaq faq2 = new ScenarioFaq();
        faq2.setQuestion("VPN 怎么配置？");
        faq2.setAnswer("请访问 https://vpn.company.com 下载客户端，使用域账号登录。");
        faq2.setKeywords("VPN,虚拟专用网络,远程办公");
        faq2.setEnabled(true);
        faqMapper.insert(faq2);

        ScenarioFaq faq3 = new ScenarioFaq();
        faq3.setQuestion("加班费怎么算？");
        faq3.setAnswer("工作日加班 1.5 倍，周末 2 倍，法定节假日 3 倍。");
        faq3.setKeywords("加班,加班费,加班工资");
        faq3.setEnabled(true);
        faqMapper.insert(faq3);

        // 禁用的 FAQ（不应被匹配）
        ScenarioFaq disabledFaq = new ScenarioFaq();
        disabledFaq.setQuestion("旧版 FAQ");
        disabledFaq.setAnswer("已废弃");
        disabledFaq.setKeywords("旧,废弃");
        disabledFaq.setEnabled(false);
        faqMapper.insert(disabledFaq);

        // 重新加载内存缓存
        faqService.reload();
    }

    @Test
    @DisplayName("精确匹配：问题文本完全匹配应返回预设答案")
    void shouldExactMatchQuestion() {
        var result = faqService.match("我有多少天年假？");
        assertNotNull(result);
        assertEquals(99, result.score());
        assertTrue(result.entry().getAnswer().contains("5天年假"));
    }

    @Test
    @DisplayName("精确匹配：问题文本模糊包含应命中")
    void shouldMatchByPartialQuestion() {
        // 用户问题包含 FAQ 问题
        var result = faqService.match("请问我有多少天年假？");
        assertNotNull(result);
        assertTrue(result.entry().getAnswer().contains("年假"));
    }

    @Test
    @DisplayName("关键词匹配：命中 ≥ 2 个关键词应返回最佳匹配")
    void shouldMatchByKeywords() {
        var result = faqService.match("年假怎么休假");
        assertNotNull(result);
        assertTrue(result.score() >= 2); // 命中"年假"+"休假" = 2
        assertTrue(result.entry().getAnswer().contains("年假"));
    }

    @Test
    @DisplayName("关键词匹配：仅命中 1 个关键词不应返回结果")
    void shouldNotMatchWhenOnlyOneKeyword() {
        // "假期"仅命中1个关键词，且不在任何FAQ问题文本中，不会触发精确匹配
        var result = faqService.match("假期");
        assertNull(result);
    }

    @Test
    @DisplayName("无匹配：不相关问题应返回 null")
    void shouldReturnNullForUnrelatedQuery() {
        var result = faqService.match("今天天气怎么样");
        assertNull(result);
    }

    @Test
    @DisplayName("禁用的 FAQ 不应被匹配")
    void shouldNotMatchDisabledFaq() {
        var result = faqService.match("旧版 FAQ");
        assertNull(result);
    }

    @Test
    @DisplayName("关键词匹配：VPN 相关问题应命中")
    void shouldMatchVpnByKeywords() {
        var result = faqService.match("远程办公怎么连VPN");
        assertNotNull(result);
        assertTrue(result.entry().getAnswer().contains("vpn.company.com"));
    }

    @Test
    @DisplayName("空查询安全处理")
    void shouldHandleNullAndEmptyQuery() {
        assertNull(faqService.match(null));
        assertNull(faqService.match(""));
    }
}
