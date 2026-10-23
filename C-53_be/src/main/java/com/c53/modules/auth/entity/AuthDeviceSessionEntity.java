package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 设备会话实体，对应 {@code auth_device_sessions}。
 *
 * <p>设备 Cookie 对应的长期登录态。Cookie 中只放原始令牌，
 * 数据库只保存其 HMAC 摘要，令牌泄露时无法从数据库反推。</p>
 */
@Data
@TableName("auth_device_sessions")
public class AuthDeviceSessionEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属设备 ID。 */
    private String deviceId;

    /** 设备令牌的 HMAC 摘要。 */
    private String tokenHash;

    /** 摘要使用的密钥版本。 */
    private Integer hmacKeyVersion;

    /** 创建时的 IP。 */
    private String createdIpAddress;

    /** 最近活跃时的 IP。 */
    private String lastIpAddress;

    /** 最近活跃时的 User-Agent。 */
    private String userAgent;

    /** 创建时间。 */
    private Instant createdAt;

    /** 最近活跃时间。 */
    private Instant lastSeenAt;

    /** 过期时间。 */
    private Instant expiresAt;

    /** 吊销时间。 */
    private Instant revokedAt;

    /** 吊销原因。 */
    private String revokedReason;

    /** 更新时间。 */
    private Instant updatedAt;
}
