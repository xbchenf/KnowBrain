package com.knowbrain.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT Token 黑名单 — 基于 Redis
 *
 * 场景：
 * - 用户主动退出登录 → 当前 Token 加入黑名单
 * - 管理员禁用用户 / 重置密码 → 用户所有 Token 加入黑名单
 *
 * 存储：
 *   Key:   kb:token:blacklist:{jti}
 *   Value: "1"
 *   TTL:   Token 剩余有效时间（过期后 Redis 自动清理，无需手动维护）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "kb:token:blacklist:";

    private final StringRedisTemplate redis;

    /**
     * 将指定 jti 加入黑名单
     * @param jti      Token 唯一 ID
     * @param expireAt Token 过期时间（秒级时间戳）
     */
    public void add(String jti, long expireAt) {
        long now = System.currentTimeMillis() / 1000;
        long ttl = expireAt - now;
        if (ttl <= 0) return; // Token 已过期，无需加入黑名单

        redis.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttl));
        log.info("Token 已加入黑名单: jti={}, ttl={}s", jti, ttl);
    }

    /**
     * 检查 jti 是否在黑名单中
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
