package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Refresh Token 家族实体，对应 {@code auth_token_families}。
 *
 * <p>同一次登录产生的所有刷新令牌属于同一家族。检测到旧令牌被重复使用时，
 * 说明令牌可能已泄露，此时吊销整个家族并记录 {@code compromisedAt}。</p>
 */
@Data
@TableName("auth_token_families")
public class AuthTokenFamilyEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属会话 ID。 */
    private String sessionId;

    /** 创建时间。 */
    private Instant createdAt;

    /** 家族过期时间，不晚于会话过期时间。 */
    private Instant expiresAt;

    /** 吊销时间。 */
    private Instant revokedAt;

    /** 吊销原因，例如 logout/compromised。 */
    private String revokedReason;

    /** 判定令牌泄露的时间。 */
    private Instant compromisedAt;
}
