package com.knowbrain.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用请求频率限制 — 基于 Redis 固定窗口计数器
 *
 * 多实例部署安全：Redis 作为共享计数器，所有实例一致限流。
 */
@Slf4j
@Component
public class RateLimiter {

    @Value("${rate-limit.rag.max-per-minute:20}")
    private int maxPerMinute;

    @Value("${rate-limit.rag.enabled:true}")
    private boolean enabled;

    private static final String KEY_PREFIX = "kb:rate:rag:";
    private static final int WINDOW_SECONDS = 60;

    /**
     * Lua 脚本：固定窗口计数 + 获取令牌
     *
     * KEYS[1] = 计数器 key  (kb:rate:rag:{userId})
     * ARGV[1] = 最大请求数
     * ARGV[2] = 窗口 TTL (秒)
     *
     * 返回: 窗口内已使用次数（含本次），-1 表示超限
     */
    private static final String LUA_TRY_ACQUIRE =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "end " +
            "if current > tonumber(ARGV[1]) then " +
            "  return -1 " +
            "end " +
            "return current";

    /**
     * Lua 脚本：获取剩余配额
     *
     * KEYS[1] = 计数器 key
     * ARGV[1] = 最大请求数
     * 返回: 剩余配额
     */
    private static final String LUA_REMAINING =
            "local current = redis.call('GET', KEYS[1]) " +
            "if not current then return tonumber(ARGV[1]) end " +
            "local remain = tonumber(ARGV[1]) - tonumber(current) " +
            "if remain < 0 then return 0 end " +
            "return remain";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> acquireScript;
    private final DefaultRedisScript<Long> remainingScript;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;

        this.acquireScript = new DefaultRedisScript<>();
        this.acquireScript.setScriptText(LUA_TRY_ACQUIRE);
        this.acquireScript.setResultType(Long.class);

        this.remainingScript = new DefaultRedisScript<>();
        this.remainingScript.setScriptText(LUA_REMAINING);
        this.remainingScript.setResultType(Long.class);
    }

    /**
     * 尝试获取令牌
     *
     * @param userId 用户 ID
     * @return true = 允许放行，false = 超出限流
     */
    public boolean tryAcquire(Long userId) {
        if (!enabled || userId == null) return true;

        String key = KEY_PREFIX + userId;
        Long result = redis.execute(acquireScript,
                List.of(key),
                String.valueOf(maxPerMinute),
                String.valueOf(WINDOW_SECONDS));

        if (result != null && result < 0) {
            log.warn("RAG 限流触发: userId={}, 每分钟限额={}", userId, maxPerMinute);
            return false;
        }
        return true;
    }

    /** 获取当前窗口剩余配额 */
    public long remainingQuota(Long userId) {
        if (userId == null) return maxPerMinute;
        String key = KEY_PREFIX + userId;
        Long result = redis.execute(remainingScript,
                List.of(key),
                String.valueOf(maxPerMinute));
        return result != null ? result : maxPerMinute;
    }
}
