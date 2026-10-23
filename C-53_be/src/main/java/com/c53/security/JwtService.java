package com.c53.security;

import com.c53.common.support.IdGenerator;
import com.c53.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析服务。
 *
 * <p>使用 HS256 对称签名，密钥由配置中的字符串经 SHA-256 派生为 256 bit。
 * Access Token 只承载用户 ID（{@code sub}）和会话 ID（{@code sid}），
 * 不放入角色、权限等易变信息，避免令牌签发后状态无法及时更新。</p>
 */
@Component
public class JwtService {

    /** 签名密钥。 */
    private final SecretKey key;

    /** 令牌签发者，解析时会校验。 */
    private final String issuer;

    /** Access Token 有效期（秒）。 */
    private final long accessTtlSeconds;

    public JwtService(AuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(sha256(properties.getJwt().getSecret()));
        this.issuer = properties.getJwt().getIssuer();
        this.accessTtlSeconds = properties.getJwt().getAccessTtl().toSeconds();
    }

    /**
     * 签发 Access Token。
     *
     * @param userId    用户 ID，写入 {@code sub}
     * @param sessionId 会话 ID，写入自定义声明 {@code sid}
     * @param now       签发时间
     * @return 紧凑格式的 JWT 字符串
     */
    public String createAccessToken(String userId, String sessionId, Instant now) {
        Instant expiresAt = now.plusSeconds(accessTtlSeconds);
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .id(IdGenerator.newId("jti"))
                .claim("sid", sessionId)
                // 用 typ 声明区分 Access Token 与 Refresh Token，防止令牌混用
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 Access Token。
     *
     * @param token JWT 字符串
     * @return 令牌中的用户、会话和过期时间
     * @throws io.jsonwebtoken.JwtException           签名错误、过期或格式非法
     * @throws IllegalArgumentException               类型不是 access，或缺少必要声明
     */
    public AccessTokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // 拒绝把 Refresh Token 当作 Access Token 使用
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("not an access token");
        }
        String sessionId = claims.get("sid", String.class);
        if (claims.getSubject() == null || sessionId == null) {
            throw new IllegalArgumentException("missing token claims");
        }
        return new AccessTokenClaims(claims.getSubject(), sessionId, claims.getExpiration().toInstant());
    }

    /**
     * 返回 Access Token 有效期（秒），用于响应体中的 {@code expiresIn}。
     *
     * @return 有效期秒数
     */
    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /**
     * Access Token 中解析出的声明。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param expiresAt 过期时间
     */
    public record AccessTokenClaims(String userId, String sessionId, Instant expiresAt) {
    }

    /**
     * 将配置中的任意长度密钥派生为 32 字节，满足 HS256 对密钥长度的要求。
     *
     * @param value 原始密钥字符串
     * @return 32 字节摘要
     */
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
