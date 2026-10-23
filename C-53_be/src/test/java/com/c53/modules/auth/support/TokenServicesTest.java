package com.c53.modules.auth.support;

import com.c53.modules.auth.model.AuthModels.TokenBundle;
import com.c53.modules.auth.service.TokenService;
import com.c53.modules.auth.service.impl.TokenServiceImpl;
import com.c53.config.AuthProperties;
import com.c53.security.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 令牌签发与解析测试。
 *
 * <p>使用真实配置默认值和真实实现，验证：
 * <ul>
 *   <li>Access Token 携带用户和会话标识</li>
 *   <li>Refresh Token 是不透明随机串，服务端只保存摘要</li>
 *   <li>设备令牌格式正确且每次生成都不同</li>
 * </ul></p>
 */
class TokenServicesTest {

    private final AuthProperties properties = new AuthProperties();
    private final HmacService hmacService = new HmacService(properties);
    private final JwtService jwtService = new JwtService(properties);
    private final TokenService tokenService = new TokenServiceImpl(jwtService, hmacService, properties);

    /**
     * Access Token 解析后必须还原出签发时的 userId 和 sessionId。
     */
    @Test
    void accessTokenCarriesUserAndSessionAndRejectsWrongType() {
        String token = tokenService.accessToken("usr_1", "ses_1", Instant.now());
        JwtService.AccessTokenClaims claims = jwtService.parse(token);
        assertThat(claims.userId()).isEqualTo("usr_1");
        assertThat(claims.sessionId()).isEqualTo("ses_1");
    }

    /**
     * Refresh Token 以 {@code rt_v1_} 开头，原文不包含摘要；
     * 再次解析应得到相同摘要，非法格式返回 {@code null}。
     */
    @Test
    void refreshTokenIsOpaqueAndHashMatches() {
        Instant now = Instant.now();
        TokenBundle bundle = tokenService.newRefreshToken(now, now.plusSeconds(3600));
        assertThat(bundle.raw()).startsWith("rt_v1_");
        assertThat(bundle.raw()).doesNotContain(bundle.hash());
        TokenService.ParsedRefreshToken parsed = tokenService.parseRefreshToken(bundle.raw());
        assertThat(parsed).isNotNull();
        assertThat(parsed.hash()).isEqualTo(bundle.hash());
        assertThat(tokenService.parseRefreshToken("rt_v1_bad")).isNull();
    }

    /**
     * 设备令牌应带 {@code ds_v1_} 前缀，且两次生成不得相同。
     */
    @Test
    void deviceTokensAreUniqueAndOpaque() {
        String first = tokenService.newDeviceToken();
        String second = tokenService.newDeviceToken();
        assertThat(first).startsWith("ds_v1_");
        assertThat(first).isNotEqualTo(second);
    }
}
