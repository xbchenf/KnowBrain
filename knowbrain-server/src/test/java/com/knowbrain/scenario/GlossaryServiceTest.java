package com.knowbrain.scenario;

import com.knowbrain.TestMockConfig;
import com.knowbrain.scenario.entity.ScenarioGlossary;
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
 * 测试 9：术语改写 — 口语 → 正式术语
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
@DisplayName("术语词典改写")
class GlossaryServiceTest {

    @Autowired
    private ScenarioGlossaryMapper glossaryMapper;

    @Autowired
    private GlossaryService glossaryService;

    @BeforeEach
    void setUp() {
        glossaryMapper.delete(null);

        // IT 术语
        insertGlossary("wifi", "无线网络", "WiFi,Wi-Fi,WLAN");
        insertGlossary("电脑卡", "系统运行缓慢", "电脑慢,卡顿,运行卡");
        insertGlossary("蓝屏", "系统蓝屏错误", "蓝底白字,BSOD");
        insertGlossary("vpn", "VPN虚拟专用网络", "VPN,虚拟专用网");

        // HR 术语
        insertGlossary("年假", "带薪年休假", "年休假,休假");
        insertGlossary("加班费", "加班工资", "加班工资,OT工资");

        glossaryService.reload();
    }

    private void insertGlossary(String term, String formal, String synonyms) {
        ScenarioGlossary g = new ScenarioGlossary();
        g.setTerm(term);
        g.setFormal(formal);
        g.setSynonyms(synonyms);
        glossaryMapper.insert(g);
    }

    @Test
    @DisplayName("口语「电脑卡」应改写为「系统运行缓慢」")
    void shouldRewriteSpokenToFormal() {
        String result = glossaryService.rewrite("电脑卡怎么办");
        assertTrue(result.contains("系统运行缓慢"));
        assertFalse(result.contains("电脑卡"));
    }

    @Test
    @DisplayName("同义词「WiFi」应改写为「无线网络」")
    void shouldRewriteSynonym() {
        String result = glossaryService.rewrite("WiFi连不上");
        assertTrue(result.contains("无线网络"));
        assertFalse(result.contains("WiFi"));
    }

    @Test
    @DisplayName("同义词「卡顿」应改写为「系统运行缓慢」")
    void shouldRewriteSynonymVariant() {
        String result = glossaryService.rewrite("电脑卡顿怎么解决");
        assertTrue(result.contains("系统运行缓慢"));
    }

    @Test
    @DisplayName("多术语场景：一句话中多个术语都应被改写")
    void shouldRewriteMultipleTerms() {
        String result = glossaryService.rewrite("电脑卡的时候WiFi也连不上");
        assertTrue(result.contains("系统运行缓慢"));
        assertTrue(result.contains("无线网络"));
    }

    @Test
    @DisplayName("无匹配术语时原文不变")
    void shouldReturnOriginalWhenNoMatch() {
        String result = glossaryService.rewrite("今天天气很好");
        assertEquals("今天天气很好", result);
    }

    @Test
    @DisplayName("空查询安全处理")
    void shouldHandleEmptyAndNull() {
        assertEquals("", glossaryService.rewrite(""));
        assertNull(glossaryService.rewrite(null));
    }

    @Test
    @DisplayName("HR 术语「年假」改写为「带薪年休假」")
    void shouldRewriteHrTerms() {
        String result = glossaryService.rewrite("年假怎么申请");
        assertTrue(result.contains("带薪年休假"));
    }
}
