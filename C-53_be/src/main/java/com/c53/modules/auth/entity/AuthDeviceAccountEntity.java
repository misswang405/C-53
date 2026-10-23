package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 设备账号关系实体，对应 {@code auth_device_accounts}。
 *
 * <p>记录某台设备上登录过并选择“保存”的账号，是已保存账号列表和
 * 一键切换账号的数据基础。唯一键为 {@code (device_id, user_id)}。</p>
 */
@Data
@TableName("auth_device_accounts")
public class AuthDeviceAccountEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 设备 ID。 */
    private String deviceId;

    /** 用户 ID。 */
    private String userId;

    /** 该账号在此设备上首次授权的时间。 */
    private Instant authorizedAt;

    /** 最近一次使用该账号的时间。 */
    private Instant lastUsedAt;

    /** 是否要求重新认证，例如密码变更或风控触发。 */
    private Boolean reauthRequired;

    /** 要求重新认证的原因。 */
    private String reauthReason;

    /** 吊销时间，非空表示该记录不再出现在已保存列表。 */
    private Instant revokedAt;

    /** 吊销原因。 */
    private String revokedReason;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;
}
