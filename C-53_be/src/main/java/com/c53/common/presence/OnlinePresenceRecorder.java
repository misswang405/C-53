package com.c53.common.presence;

/**
 * 在线活跃记录。
 *
 * <p>定义在 {@code common} 包，供安全过滤器在认证成功后上报活跃用户，
 * 避免 {@code security} 反向依赖具体业务模块。实现对 Redis 故障必须静默降级。</p>
 */
public interface OnlinePresenceRecorder {

    /**
     * 记录一次用户活跃。实现应自行做节流与异常兜底。
     *
     * @param userId 用户 ID
     */
    void touch(String userId);

    /**
     * 统计当前在线用户数。
     *
     * @return 在线人数；依赖不可用时返回 0
     */
    long count();
}
