package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户协议同意记录实体，对应 {@code auth_user_agreements}。
 *
 * <p>合规审计表，记录用户同意的协议类型、版本以及同意时的请求上下文。
 * 唯一键保证同一用户同一协议版本只保留一条记录。</p>
 */
@Data
@TableName("auth_user_agreements")
public class AuthUserAgreementEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private String userId;

    /** 协议类型，例如 terms/privacy。 */
    private String agreementType;

    /** 协议版本号。 */
    private String version;

    /** 同意时间。 */
    private Instant agreedAt;

    /** 请求追踪 ID。 */
    private String requestId;

    /** 同意时的客户端 IP。 */
    private String ipAddress;

    /** 同意时的 User-Agent。 */
    private String userAgent;
}
