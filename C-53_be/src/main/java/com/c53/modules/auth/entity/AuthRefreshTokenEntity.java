package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Refresh Token 签发审计实体，对应 {@code auth_refresh_tokens}。
 *
 * <p>每次签发（包括轮换）写一条，通过 {@code parentTokenId} 形成链条，
 * 用于追溯令牌来源。令牌有效性以 Redis 白名单为准，本表不参与实时校验。</p>
 */
@Data
@TableName("auth_refresh_tokens")
public class AuthRefreshTokenEntity {

    /** 令牌 ID，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属令牌家族 ID。 */
    private String familyId;

    /** 令牌的 HMAC 摘要，不保存明文。 */
    private String tokenHash;

    /** 摘要使用的密钥版本。 */
    private Integer hmacKeyVersion;

    /** 轮换前的父令牌 ID，首个令牌为空。 */
    private String parentTokenId;

    /** 签发时间。 */
    private Instant issuedAt;

    /** 过期时间。 */
    private Instant expiresAt;

    /** 签发请求的追踪 ID。 */
    private String requestId;

    /** 签发时的客户端 IP。 */
    private String createdIpAddress;
}
