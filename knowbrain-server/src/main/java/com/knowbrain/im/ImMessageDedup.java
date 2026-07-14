package com.knowbrain.im;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * IM 消息去重 — 基于 Redis SETNX 原子操作。
 *
 * <h3>为什么需要去重</h3>
 * 各 IM 平台在以下场景会重复推送同一条消息：
 * <ul>
 *   <li>企微：回调超时未响应，5 秒后重试</li>
 *   <li>钉钉：HTTP 回调返回非 200，以递增间隔重试</li>
 *   <li>飞书：3 秒内未返回 200 → 15s / 5min / 1h / 6h 共 4 次重试</li>
 * </ul>
 * 最坏情况下同一条消息可能被推送 5 次，不加去重会导致：
 * <ul>
 *   <li>RAG 重复调用，浪费 LLM Token</li>
 *   <li>用户收到多条重复回复</li>
 * </ul>
 *
 * <h3>实现策略</h3>
 * Redis Key: {@code kb:im:dedup:{platform}:{messageId}}<br>
 * TTL: 8 小时（覆盖飞书最长 7.1 小时重试窗口 + 缓冲）<br>
 * 操作: SETNX（原子性保证多实例安全）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImMessageDedup {

    private final StringRedisTemplate redis;

    /** Redis Key 前缀 */
    private static final String KEY_PREFIX = "kb:im:dedup:";

    /** TTL：8 小时（飞书最长重试 7.1h + 缓冲） */
    private static final Duration TTL = Duration.ofHours(8);

    /**
     * 标记消息已处理并检查是否重复。
     *
     * <p>原子操作：如果 key 不存在则设置并返回 true（首次处理），
     * 如果 key 已存在则返回 false（重复消息）。
     *
     * @param platform  平台标识（wecom / dingtalk / feishu）
     * @param messageId 平台原生消息 ID
     * @return true = 首次处理，可以继续；false = 重复消息，应跳过
     */
    public boolean markAndCheck(String platform, String messageId) {
        if (platform == null || messageId == null) {
            log.warn("去重参数为 null: platform={}, messageId={}", platform, messageId);
            return true; // 无法去重时放行，避免丢失消息
        }

        String key = KEY_PREFIX + platform + ":" + messageId;

        // SETNX: 仅当 key 不存在时设置成功
        Boolean success = redis.opsForValue().setIfAbsent(key, "1", TTL);

        if (Boolean.FALSE.equals(success)) {
            log.debug("IM 消息重复，跳过: platform={}, messageId={}", platform, messageId);
            return false;
        }

        return true;
    }

    /**
     * 手动清除去重记录（用于测试或异常恢复场景）。
     *
     * @param platform  平台标识
     * @param messageId 消息 ID
     */
    public void clear(String platform, String messageId) {
        String key = KEY_PREFIX + platform + ":" + messageId;
        redis.delete(key);
        log.debug("已清除去重记录: {}", key);
    }
}
