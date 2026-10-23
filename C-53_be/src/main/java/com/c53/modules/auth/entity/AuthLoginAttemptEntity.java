package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 登录尝试实体，对应 {@code auth_login_attempts}。
 *
 * <p>追加式审计表：每次登录无论成功失败都写一条。
 * 账号以 HMAC 摘要保存，既可用于失败计数，又避免明文联系方式落库。</p>
 */
@Data
@TableName("auth_login_attempts")
public class AuthLoginAttemptEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 匹配到的用户 ID，账号不存在时为空。 */
    private String userId;

    /** 账号类型：email/phone/username。 */
    private String accountType;

    /** 规范化账号的 HMAC 摘要。 */
    private String accountHash;

    /** 摘要使用的密钥版本。 */
    private Integer hmacKeyVersion;

    /** 发起登录的设备 ID。 */
    private String deviceId;

    /** 登录成功后的会话 ID，失败时为空。 */
    private String sessionId;

    /** 登录方式：password/code/switch。 */
    private String loginMethod;

    /** 是否成功。 */
    private Boolean success;

    /** 失败错误码，成功时为空。 */
    private String failureCode;

    /** 请求追踪 ID。 */
    private String requestId;

    /** 客户端 IP。 */
    private String ipAddress;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 尝试时间。 */
    private Instant attemptedAt;
}
