package com.c53.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的固定窗口限流器。
 *
 * <p>使用 Lua 脚本保证 {@code INCR} 与 {@code PEXPIRE} 的原子性。
 * Redis 不可用时按 fail-open 放行并记录告警，避免限流组件拖垮主链路；
 * 发布幂等与上传安全另有数据库唯一键、STS 前缀和确认校验兜底。</p>
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /** 原子自增并在首次写入时设置过期时间。 */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试获取一次计数。
     *
     * @param key    限流 Key
     * @param limit  窗口内最大次数
     * @param window 窗口长度
     * @return 限流结果
     */
    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        try {
            Long current = redis.execute(INCREMENT_SCRIPT, List.of(key),
                    String.valueOf(window.toMillis()));
            long count = current == null ? 0 : current;
            if (count <= limit) {
                return RateLimitResult.allowed(limit - count);
            }
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            return RateLimitResult.rejected(ttl == null ? 1 : ttl);
        } catch (RuntimeException exception) {
            // Redis 故障时放行，保证发布主链路可用
            log.warn("rate limiter unavailable, fail-open key={}", key, exception);
            return RateLimitResult.allowed(limit);
        }
    }

    /**
     * 回退一次计数，用于上传失败后归还配额。
     *
     * @param key 限流 Key
     */
    public void decrement(String key) {
        try {
            redis.opsForValue().decrement(key);
        } catch (RuntimeException exception) {
            log.warn("rate limiter decrement failed key={}", key, exception);
        }
    }

    /**
     * 读取当前计数，不存在时返回 0。
     *
     * @param key 限流 Key
     * @return 当前计数
     */
    public long current(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException exception) {
            log.warn("rate limiter read failed key={}", key, exception);
            return 0;
        }
    }
}
