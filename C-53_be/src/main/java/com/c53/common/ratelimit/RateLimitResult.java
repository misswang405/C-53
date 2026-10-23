package com.c53.common.ratelimit;

/**
 * 限流结果。
 *
 * @param allowed           是否放行
 * @param remaining         当前窗口剩余次数
 * @param retryAfterSeconds 建议重试等待秒数
 */
public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {

    /**
     * 构造放行结果。
     *
     * @param remaining 剩余次数
     * @return 放行结果
     */
    public static RateLimitResult allowed(long remaining) {
        return new RateLimitResult(true, remaining, 0);
    }

    /**
     * 构造拒绝结果。
     *
     * @param retryAfterSeconds 建议重试等待秒数
     * @return 拒绝结果
     */
    public static RateLimitResult rejected(long retryAfterSeconds) {
        return new RateLimitResult(false, 0, Math.max(1, retryAfterSeconds));
    }
}
