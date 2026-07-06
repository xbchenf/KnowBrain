package com.knowbrain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("密码强度校验")
class PasswordValidatorTest {

    @Test
    @DisplayName("合法密码：大小写 + 数字 + 特殊字符")
    void shouldAcceptStrongPassword() {
        assertDoesNotThrow(() -> PasswordValidator.validate("KnowBrain@2026"));
    }

    @Test
    @DisplayName("合法密码：大小写 + 数字（3 类）")
    void shouldAcceptUpperLowerDigit() {
        assertDoesNotThrow(() -> PasswordValidator.validate("Abc12345"));
    }

    @Test
    @DisplayName("合法密码：小写 + 数字 + 特殊字符（3 类）")
    void shouldAcceptLowerDigitSpecial() {
        assertDoesNotThrow(() -> PasswordValidator.validate("abc123!@#"));
    }

    @Test
    @DisplayName("太短：7 位 → 拒绝")
    void shouldRejectTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PasswordValidator.validate("Ab1!567"));
        assertTrue(ex.getMessage().contains("至少 8 位"));
    }

    @Test
    @DisplayName("复杂度不足：纯字母 → 拒绝")
    void shouldRejectOnlyLetters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PasswordValidator.validate("Abcdefgh"));
        assertTrue(ex.getMessage().contains("至少 3 类"));
    }

    @Test
    @DisplayName("复杂度不足：纯数字 → 拒绝")
    void shouldRejectOnlyDigits() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PasswordValidator.validate("12345678"));
        assertTrue(ex.getMessage().contains("至少 3 类"));
    }

    @Test
    @DisplayName("弱密码：123456 → 拒绝")
    void shouldRejectWeakPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordValidator.validate("123456"));
    }
}
