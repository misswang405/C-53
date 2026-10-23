package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 登录会话实体，对应 {@code auth_sessions}。
 *
 * <p>一次登录对应一个会话。Access Token 的 {@code sid} 声明指向该记录，
 * 会话被吊销后，即使 Access Token 尚未过期，也应在业务层拒绝其继续使用。</p>
 */
@Data
@TableName("auth_sessions")
public class AuthSessionEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户 ID。 */
    private String userId;

    /** 设备 ID。 */
    private String deviceId;

    /** 关联的设备会话 ID。 */
    private String deviceSessionId;

    /** 登录方式：password/code/switch。 */
    private String loginMethod;

    /** 是否保持登录，决定 Refresh Token 有效期。 */
    private Boolean rememberMe;

    /** 登录时 IP。 */
    private String ipAddress;

    /** 登录时 User-Agent。 */
    private String userAgent;

    /** 创建时间。 */
    private Instant createdAt;

    /** 最近活跃时间。 */
    private Instant lastActiveAt;

    /** 会话过期时间。 */
    private Instant expiresAt;

    /** 吊销时间。 */
    private Instant revokedAt;

    /** 吊销原因。 */
    private String revokedReason;

    /** 更新时间。 */
    private Instant updatedAt;
}
