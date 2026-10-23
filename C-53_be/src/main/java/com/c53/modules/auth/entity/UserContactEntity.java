package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户联系方式实体，对应 {@code user_contacts}。
 *
 * <p>一个用户可绑定多个邮箱/手机号。{@code is_primary} 标记主联系方式，
 * 未删除的有效记录参与全局唯一性校验。</p>
 */
@Data
@TableName("user_contacts")
public class UserContactEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户 ID。 */
    private String userId;

    /** 联系方式类型：email/phone。 */
    private String contactType;

    /** 规范化后的联系方式值。 */
    private String contactValue;

    /** 是否已验证。 */
    private Boolean verified;

    /** 验证时间。 */
    private Instant verifiedAt;

    /** 是否为主联系方式。 */
    private Boolean isPrimary;

    /** 状态，例如 active/removed。 */
    private String status;

    /** 解绑时间。 */
    private Instant removedAt;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;
}
