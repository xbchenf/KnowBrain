package com.knowbrain.auth;

/**
 * 密码强度校验工具
 *
 * 规则：至少 8 位，必须包含大写字母、小写字母、数字、特殊字符中至少 3 类
 */
public final class PasswordValidator {

    private static final int MIN_LENGTH = 8;

    private PasswordValidator() {}

    /**
     * 校验密码强度，不合法时抛出 BizException(400, reason)
     */
    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        // 长度
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密码至少 " + MIN_LENGTH + " 位，当前 " + password.length() + " 位");
        }

        // 复杂度：大小写字母 + 数字 + 特殊字符，至少满足 3 类
        int categories = 0;
        if (password.matches(".*[A-Z].*")) categories++;
        if (password.matches(".*[a-z].*")) categories++;
        if (password.matches(".*[0-9].*")) categories++;
        if (password.matches(".*[^A-Za-z0-9].*")) categories++;

        if (categories < 3) {
            throw new IllegalArgumentException(
                    "密码需包含大写字母、小写字母、数字、特殊字符中至少 3 类（当前仅 " + categories + " 类）");
        }
    }
}
