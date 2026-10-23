package com.c53.modules.auth.service;

import com.c53.modules.auth.model.AuthModels.TokenBundle;

import java.time.Duration;
import java.time.Instant;

/**
 * 令牌生成与解析服务。
 *
 * <p>统一管理 Access Token、Refresh Token 和设备令牌的格式、有效期与摘要计算，
 * 避免各处重复实现令牌规则。</p>
 */
public interface TokenService {

    /**
     * 签发 Access Token。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param now       签发时间
     * @return JWT 字符串
     */
    String accessToken(String userId, String sessionId, Instant now);

    /**
     * 返回 Access Token 有效期（秒）。
     *
     * @return 有效期秒数
     */
    long accessTtlSeconds();

    /**
     * 返回 Refresh Token 有效期。
     *
     * @param remember 是否保持登录
     * @return 7 天或 24 小时
     */
    Duration refreshTtl(boolean remember);

    /**
     * 生成新的 Refresh Token。
     *
     * @param now       签发时间
     * @param expiresAt 过期时间
     * @return 原始令牌、摘要、令牌 ID 和时间信息
     */
    TokenBundle newRefreshToken(Instant now, Instant expiresAt);

    /**
     * 校验 Refresh Token 格式并计算摘要。
     *
     * @param raw 原始令牌
     * @return 解析结果；格式非法时返回 {@code null}
     */
    ParsedRefreshToken parseRefreshToken(String raw);

    /**
     * 生成新的设备令牌。
     *
     * @return 形如 {@code ds_v1_<随机值>} 的令牌
     */
    String newDeviceToken();

    /**
     * 解析后的 Refresh Token。
     *
     * @param raw  原始令牌
     * @param hash HMAC 摘要
     */
    record ParsedRefreshToken(String raw, String hash) {
    }
}
