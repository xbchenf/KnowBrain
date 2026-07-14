package com.knowbrain.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登录频率限制 — 基于 Redis 滑动窗口
 * 防止暴力破解：同一 IP 5 分钟内失败 5 次后锁定 15 分钟
 *
 * 多实例部署安全：Redis 作为共享计数器，所有实例一致限流。
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final int FAILURE_WINDOW_SECONDS = 300;  // 5 分钟
    private static final int LOCK_SECONDS = 900;             // 15 分钟

    private static final String KEY_PREFIX = "kb:rate:login:";
    private static final String LOCK_PREFIX = "kb:rate:login:lock:";

    /**
     * Lua 脚本：滑动窗口记录失败 + 检查是否需要锁定
     *
     * KEYS[1] = 失败记录 key  (kb:rate:login:{ip})
     * KEYS[2] = 锁定 key      (kb:rate:login:lock:{ip})
     * ARGV[1] = 当前时间戳 (ms)
     * ARGV[2] = 失败窗口 (ms)
     * ARGV[3] = 最大失败次数
     * ARGV[4] = 锁定时长 (秒)
     *
     * 返回: 1=触发锁定, 0=正常记录
     */
    private static final String LUA_RECORD_FAILURE =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, " +
            "  tonumber(ARGV[1]) - tonumber(ARGV[2])) " +
            "local now = ARGV[1] " +
            "redis.call('ZADD', KEYS[1], now, now .. ':' .. redis.call('ZCARD', KEYS[1])) " +
            "redis.call('EXPIRE', KEYS[1], math.ceil(tonumber(ARGV[2]) / 1000) + 60) " +
            "if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then " +
            "  redis.call('SET', KEYS[2], now, 'EX', ARGV[4]) " +
            "  redis.call('DEL', KEYS[1]) " +
            "  return 1 " +
            "end " +
            "return 0";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> recordScript;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.recordScript = new DefaultRedisScript<>();
        this.recordScript.setScriptText(LUA_RECORD_FAILURE);
        this.recordScript.setResultType(Long.class);
    }

    /**
     * 检查该 IP 是否被锁定
     */
    public boolean allow(String ip) {
        Boolean locked = redis.hasKey(LOCK_PREFIX + ip);
        if (Boolean.TRUE.equals(locked)) {
            Long ttl = redis.getExpire(LOCK_PREFIX + ip);
            log.warn("登录限速: IP={} 锁定中, 剩余{}秒", ip, ttl != null ? ttl : 0);
            return false;
        }
        return true;
    }

    /** 记录一次登录失败，若达到阈值则触发锁定 */
    public void recordFailure(String ip) {
        String key = KEY_PREFIX + ip;
        String lockKey = LOCK_PREFIX + ip;

        Long result = redis.execute(recordScript,
                List.of(key, lockKey),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(FAILURE_WINDOW_SECONDS * 1000L),
                String.valueOf(MAX_FAILURES),
                String.valueOf(LOCK_SECONDS));

        if (result != null && result == 1) {
            log.warn("登录限速触发锁定: IP={}, 锁定{}秒", ip, LOCK_SECONDS);
        } else {
            log.debug("登录失败记录: IP={}", ip);
        }
    }

    /** 登录成功后清除该 IP 的失败记录和锁定 */
    public void clearFailure(String ip) {
        redis.delete(List.of(KEY_PREFIX + ip, LOCK_PREFIX + ip));
    }

    /** 获取剩余锁定秒数，0 表示未锁定 */
    public long remainingLockSeconds(String ip) {
        Long ttl = redis.getExpire(LOCK_PREFIX + ip);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
