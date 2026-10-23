package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户实体，对应 {@code users}。
 *
 * <p>只保存账号核心信息和资料字段；邮箱、手机号等可多值联系方式
 * 存放在 {@code user_contacts}，查询时由 Repository 联表补充。</p>
 */
@Data
@TableName("users")
public class UserEntity {

    /** 主键，由应用生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户名，登录用，全局唯一。 */
    private String username;

    /** 昵称。 */
    private String nickname;

    /** 头像占位文字，取昵称首字符。 */
    private String initials;

    /** 密码哈希。 */
    private String passwordHash;

    /** 密码算法标识，例如 bcrypt。 */
    private String passwordAlgorithm;

    /** 头像地址。 */
    private String avatarUrl;

    /** 职业。 */
    private String profession;

    /** 城市。 */
    private String city;

    /** 个人简介。 */
    private String bio;

    /** 用户状态：active/disabled/deleted。 */
    private String status;

    /** 禁用原因。 */
    private String disabledReason;

    /** 最近登录时间。 */
    private Instant lastLoginAt;

    /** 最近修改密码时间。 */
    private Instant passwordChangedAt;

    /** 软删除时间。 */
    private Instant deletedAt;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;
}
