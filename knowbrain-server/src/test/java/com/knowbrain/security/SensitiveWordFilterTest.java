package com.knowbrain.security;

import com.knowbrain.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 10：敏感词过滤 — DFA 算法检测 + 脱敏
 */
@SpringBootTest
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DisplayName("敏感词过滤")
class SensitiveWordFilterTest {

    @Autowired
    private SensitiveWordFilter filter;

    @Test
    @DisplayName("含内置敏感词的文本应被检测到")
    void shouldDetectBuiltinSensitiveWord() {
        assertTrue(filter.contains("这是敏感词1的测试"));
    }

    @Test
    @DisplayName("普通文本不应包含敏感词")
    void shouldNotDetectNormalText() {
        assertFalse(filter.contains("VPN 配置教程"));
        assertFalse(filter.contains("年假申请流程"));
    }

    @Test
    @DisplayName("敏感词应被替换为星号")
    void shouldReplaceSensitiveWords() {
        String result = filter.replace("包含敏感词1的内容");
        assertFalse(result.contains("敏感词1"));
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("手机号中间四位应被掩码")
    void shouldMaskPhoneNumber() {
        assertEquals("138****5678", SensitiveWordFilter.maskPhone("13812345678"));
    }

    @Test
    @DisplayName("邮箱应被掩码")
    void shouldMaskEmail() {
        String masked = SensitiveWordFilter.maskEmail("zhangsan@company.com");
        assertFalse(masked.contains("zhangsan"));
        assertTrue(masked.contains("@"));
    }

    @Test
    @DisplayName("身份证号中间八位应被掩码")
    void shouldMaskIdCard() {
        String masked = SensitiveWordFilter.maskIdCard("320102199001011234");
        assertTrue(masked.contains("****"));
        assertFalse(masked.contains("19900101"));
    }

    @Test
    @DisplayName("sanitize 应同时处理敏感词和 PII")
    void sanitizeShouldHandleBoth() {
        // 手机号 + 敏感词
        String input = "联系 13812345678，话说敏感词1是什么";
        String result = filter.sanitize(input);
        assertFalse(result.contains("敏感词1"));
        assertFalse(result.contains("13812345678"));
    }

    @Test
    @DisplayName("空文本安全处理")
    void shouldHandleEmptyAndNull() {
        assertEquals("", filter.replace(""));
        assertNull(SensitiveWordFilter.maskPhone(null));
        assertFalse(filter.contains(null));
    }
}
