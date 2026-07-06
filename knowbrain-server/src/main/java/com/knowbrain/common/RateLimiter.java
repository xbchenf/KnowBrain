package com.knowbrain.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用请求频率限制 — 基于用户 ID 的滑动窗口计数器
 *
 * 单实例内存存储，适合 Docker Compose 部署场景。
 */
@Slf4j
@Component
public class RateLimiter {

    @Value("${rate-limit.rag.max-per-minute:20}")
    private int maxPerMinute;

    @Value("${rate-limit.rag.enabled:true}")
    private boolean enabled;

    /** userId → 窗口起始时间 + 计数器 */
    private final Map<Long, WindowCounter> counters = new ConcurrentHashMap<>();

    /**
     * 尝试获取令牌
     *
     * @param userId 用户 ID
     * @return true = 允许放行，false = 超出限流
     */
    public boolean tryAcquire(Long userId) {
        if (!enabled || userId == null) return true;

        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(userId, k -> new WindowCounter(now));

        synchronized (counter) {
            // 窗口过期 → 重置
            if (now - counter.windowStart > 60_000) {
                counter.windowStart = now;
                counter.count = 1;
                return true;
            }

            if (counter.count >= maxPerMinute) {
                long waitSeconds = 60 - (now - counter.windowStart) / 1000;
                log.warn("RAG 限流触发: userId={}, 当前窗口已请求 {} 次, 剩余等待 {}s",
                        userId, counter.count, Math.max(0, waitSeconds));
                return false;
            }

            counter.count++;
            return true;
        }
    }

    /** 获取当前窗口剩余配额（用于调试/监控） */
    public int remainingQuota(Long userId) {
        if (userId == null) return maxPerMinute;
        WindowCounter counter = counters.get(userId);
        if (counter == null) return maxPerMinute;
        synchronized (counter) {
            if (System.currentTimeMillis() - counter.windowStart > 60_000) {
                return maxPerMinute;
            }
            return Math.max(0, maxPerMinute - counter.count);
        }
    }

    private static class WindowCounter {
        long windowStart;
        int count;

        WindowCounter(long windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }
    }
}
