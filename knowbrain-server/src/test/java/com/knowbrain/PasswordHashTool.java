package com.knowbrain;

import cn.hutool.crypto.digest.BCrypt;

/**
 * 密码哈希生成工具 — 用于手动重置管理员密码
 *
 * 使用方法：运行 main()，将输出的哈希值手动更新到 MySQL：
 *   UPDATE kb_sys_user SET password_hash = '<输出值>' WHERE username = 'admin';
 */
public class PasswordHashTool {

    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "KnowBrain@2026";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println("密码: " + password);
        System.out.println("哈希: " + hash);
        System.out.println();
        System.out.println("-- 更新 SQL：");
        System.out.println("UPDATE kb_sys_user SET password_hash = '" + hash + "' WHERE username = 'admin';");
    }
}
