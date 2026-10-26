package com.c53.modules.discovery.service.impl;

import com.c53.common.ApiException;
import com.c53.common.presence.OnlinePresenceRecorder;
import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.modules.discovery.dto.CommunityDtos.SummaryResponse;
import com.c53.modules.discovery.service.CommunityService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 社区概况实现。
 *
 * <p>在线人数由 {@link OnlinePresenceRecorder} 提供，Redis 故障时返回 0，
 * 不把依赖故障传递给首页。</p>
 */
@Service
public class CommunityServiceImpl implements CommunityService {

    /** 概况接口每 IP 每分钟请求上限。 */
    private static final int SUMMARY_PER_MINUTE = 60;

    private final OnlinePresenceRecorder onlinePresenceRecorder;
    private final RedisRateLimiter rateLimiter;

    public CommunityServiceImpl(OnlinePresenceRecorder onlinePresenceRecorder,
                                RedisRateLimiter rateLimiter) {
        this.onlinePresenceRecorder = onlinePresenceRecorder;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public SummaryResponse summary(String clientIp) {
        // 限流
        String key = "discovery:rate:summary:ip:" + safeIp(clientIp);
        // 尝试获取令牌
        RateLimitResult result = rateLimiter.tryAcquire(key, SUMMARY_PER_MINUTE, Duration.ofMinutes(1));
        // 如果不允许访问
        if (!result.allowed()) {
            // 抛出异常
            throw ApiException.tooManyRequests("RATE_LIMITED", "操作过于频繁，请稍后再试",
                    Map.of("retryAfter", result.retryAfterSeconds()));
        }
        // 获取在线人数
        return new SummaryResponse(onlinePresenceRecorder.count());
    }

    private String safeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }
}
