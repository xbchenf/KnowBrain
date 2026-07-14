package com.knowbrain.auth;

/**
 * 系统角色枚举
 *
 * 替代硬编码的 "ADMIN" / "MANAGER" / "USER" 字符串，
 * 提供统一的角色匹配和设置入口。
 */
public enum RoleEnum {

    /** 系统管理员 — 全局读写、跨部门访问、用户和部门管理 */
    ADMIN("ADMIN"),

    /** 知识管理员 — 公开空间 + 本部门团队空间读写，管理接口仅 GET */
    MANAGER("MANAGER"),

    /** 普通用户 — 按空间可见性 + 成员关系决定读写权限 */
    USER("USER");

    private final String code;

    RoleEnum(String code) {
        this.code = code;
    }

    /** 角色代码，对应 DB 存储值和 JWT claims 中的 role 字段 */
    public String getCode() {
        return code;
    }

    /**
     * 比较传入的角色字符串是否与当前枚举值匹配
     * 例如：RoleEnum.ADMIN.matches(role) 替代 "ADMIN".equals(role)
     */
    public boolean matches(String role) {
        return this.code.equals(role);
    }

    /**
     * 从角色代码反查枚举值，找不到返回 null
     */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum r : values()) {
            if (r.code.equals(code)) return r;
        }
        return null;
    }
}
