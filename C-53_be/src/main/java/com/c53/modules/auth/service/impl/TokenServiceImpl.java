package com.c53.modules.auth.service.impl;

import com.c53.common.support.IdGenerator;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.model.AuthModels.TokenBundle;
import com.c53.modules.auth.service.TokenService;
import com.c53.modules.auth.support.HmacService;
import com.c53.security.JwtService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 令牌服务实现。
 *
 * <p>Refresh Token 和设备令牌使用 32 字节安全随机数 + URL 安全 Base64，
 * 带版本前缀便于后续平滑升级格式。Refresh Token 只把摘要交给存储层。</p>
 */
@Service
public class TokenServiceImpl implements TokenService {

    /** Refresh Token 格式：{@code rt_v1_} + 40-64 位 URL 安全字符。 */
    private static final Pattern REFRESH_TOKEN = Pattern.compile("^rt_v1_([A-Za-z0-9_-]{40,64})$");

    /** 密码学安全随机源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 无填充 URL 安全 Base64，避免令牌中出现 {@code + / =} 等字符。 */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JwtService jwtService;
    private final HmacService hmacService;
    private final AuthProperties properties;

    public TokenServiceImpl(JwtService jwtService, HmacService hmacService, AuthProperties properties) {
        this.jwtService = jwtService;
        this.hmacService = hmacService;
        this.properties = properties;
    }

    /**
     * 委托 {@link JwtService} 签发 Access Token。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param now       签发时间
     * @return JWT
     */
    @Override
    public String accessToken(String userId, String sessionId, Instant now) {
        return jwtService.createAccessToken(userId, sessionId, now);
    }

    /**
     * 返回 Access Token 有效期。
     *
     * @return 秒数
     */
    @Override
    public long accessTtlSeconds() {
        return jwtService.accessTtlSeconds();
    }

    /**
     * 根据“保持登录”选择 Refresh Token 有效期。
     *
     * @param remember 是否保持登录
     * @return 7 天或 24 小时
     */
    @Override
    public Duration refreshTtl(boolean remember) {
        return remember ? properties.getRefresh().getRememberTtl() : properties.getRefresh().getShortTtl();
    }

    /**
     * 生成新的 Refresh Token 及其摘要。
     *
     * @param now       签发时间
     * @param expiresAt 过期时间
     * @return 令牌信息
     */
    @Override
    public TokenBundle newRefreshToken(Instant now, Instant expiresAt) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String raw = "rt_v1_" + ENCODER.encodeToString(random);
        return new TokenBundle(raw, hmacService.hashRefreshToken(raw),
                IdGenerator.newId("rt"), now, expiresAt);
    }

    /**
     * 校验 Refresh Token 格式并计算摘要。
     *
     * @param raw 原始令牌
     * @return 解析结果；格式不符合预期时返回 {@code null}
     */
    @Override
    public ParsedRefreshToken parseRefreshToken(String raw) {
        if (raw == null || !REFRESH_TOKEN.matcher(raw).matches()) {
            return null;
        }
        return new ParsedRefreshToken(raw, hmacService.hashRefreshToken(raw));
    }

    /**
     * 生成新的设备令牌。
     *
     * @return {@code ds_v1_<随机值>}
     */
    @Override
    public String newDeviceToken() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return "ds_v1_" + ENCODER.encodeToString(random);
    }
}
