package com.knowbrain.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录频率限制 — 基于 IP 的内存计数器
 * 防止暴力破解：同一 IP 5 分钟内失败 5 次后锁定 15 分钟
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final int FAILURE_WINDOW_MINUTES = 5;
    private static final int LOCK_MINUTES = 15;

    private final Map<String, FailureRecord> records = new ConcurrentHashMap<>();

    /**
     * 检查是否允许该 IP 尝试登录
     */
    public boolean allow(String ip) {
        cleanExpired();
        FailureRecord record = records.get(ip);
        if (record == null) return true;

        long lockUntil = record.firstFailureTime + LOCK_MINUTES * 60_000L;
        if (record.failureCount >= MAX_FAILURES && System.currentTimeMillis() < lockUntil) {
            log.warn("登录限速: IP={} 已被锁定, 失败次数={}", ip, record.failureCount);
            return false;
        }
        if (record.failureCount >= MAX_FAILURES && System.currentTimeMillis() >= lockUntil) {
            records.remove(ip);
        }
        return true;
    }

    /** 记录一次登录失败 */
    public void recordFailure(String ip) {
        FailureRecord record = records.computeIfAbsent(ip, k -> new FailureRecord());
        long windowStart = System.currentTimeMillis() - FAILURE_WINDOW_MINUTES * 60_000L;

        if (record.firstFailureTime < windowStart) {
            record.failureCount = 0;
            record.firstFailureTime = System.currentTimeMillis();
        }
        record.failureCount++;
        log.debug("登录失败: IP={}, 累计失败={}", ip, record.failureCount);
    }

    /** 登录成功后清除该 IP 的失败记录 */
    public void clearFailure(String ip) {
        records.remove(ip);
    }

    /** 获取剩余锁定秒数，0 表示未锁定 */
    public long remainingLockSeconds(String ip) {
        FailureRecord record = records.get(ip);
        if (record == null || record.failureCount < MAX_FAILURES) return 0;

        long lockUntil = record.firstFailureTime + LOCK_MINUTES * 60_000L;
        long remaining = (lockUntil - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        long expireThreshold = now - (LOCK_MINUTES + FAILURE_WINDOW_MINUTES) * 60_000L;
        records.entrySet().removeIf(e ->
                e.getValue().failureCount < MAX_FAILURES
                        ? e.getValue().firstFailureTime < now - FAILURE_WINDOW_MINUTES * 60_000L
                        : e.getValue().firstFailureTime < expireThreshold
        );
    }

    private static class FailureRecord {
        long firstFailureTime = System.currentTimeMillis();
        int failureCount;
    }
}
