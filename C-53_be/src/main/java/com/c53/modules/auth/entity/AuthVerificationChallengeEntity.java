package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 验证挑战实体，对应 {@code auth_verification_challenges}。
 *
 * <p>每次发送验证码生成一条挑战。验证码本身不落库，只保存
 * {@code challengeId + code} 的 HMAC 摘要；同时记录尝试次数、
 * 过期时间和状态，防止暴力枚举和重复使用。</p>
 */
@Data
@TableName("auth_verification_challenges")
public class AuthVerificationChallengeEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 关联用户 ID，注册场景可为空。 */
    private String userId;

    /** 发起设备 ID。 */
    private String deviceId;

    /** 使用场景：register/login。 */
    private String scene;

    /** 发送渠道：sms/email。 */
    private String channel;

    /** 规范化后的接收方。 */
    private String target;

    /** 验证码摘要。 */
    private String codeHash;

    /** 摘要使用的密钥版本。 */
    private Integer hmacKeyVersion;

    /** 状态：pending/consumed/invalidated/expired。 */
    private String status;

    /** 已尝试校验次数。 */
    private Integer attempts;

    /** 最大允许尝试次数。 */
    private Integer maxAttempts;

    /** 可重发间隔秒数。 */
    private Integer resendAfterSeconds;

    /** 请求追踪 ID。 */
    private String requestId;

    /** 请求来源 IP。 */
    private String requestIpAddress;

    /** 请求 User-Agent。 */
    private String userAgent;

    /** 发送服务商标识。 */
    private String provider;

    /** 发送服务商消息 ID，用于对账。 */
    private String providerMessageId;

    /** 发送失败错误码。 */
    private String sendErrorCode;

    /** 发送成功时间。 */
    private Instant sentAt;

    /** 过期时间。 */
    private Instant expiresAt;

    /** 最近一次校验尝试时间。 */
    private Instant lastAttemptAt;

    /** 校验成功消费时间。 */
    private Instant consumedAt;

    /** 主动失效时间。 */
    private Instant invalidatedAt;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;
}
