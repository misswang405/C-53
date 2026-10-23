package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 设备实体，对应 {@code auth_devices}。
 *
 * <p>设备身份由浏览器指纹摘要识别，不使用客户端可随意伪造的自增 ID。
 * 记录设备状态和风险等级，为后续风控预留字段。</p>
 */
@Data
@TableName("auth_devices")
public class AuthDeviceEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 设备指纹的 HMAC 摘要。 */
    private String fingerprintHash;

    /** 指纹摘要使用的密钥版本。 */
    private Integer fingerprintKeyVersion;

    /** 设备状态，例如 active/revoked。 */
    private String status;

    /** 风险等级，例如 low/medium/high。 */
    private String riskLevel;

    /** 首次出现时的 IP。 */
    private String createdIpAddress;

    /** 最近一次出现时的 IP。 */
    private String lastIpAddress;

    /** 最近一次出现的 User-Agent。 */
    private String lastUserAgent;

    /** 首次出现时间。 */
    private Instant firstSeenAt;

    /** 最近出现时间。 */
    private Instant lastSeenAt;

    /** 吊销时间。 */
    private Instant revokedAt;

    /** 吊销原因。 */
    private String revokedReason;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;
}
