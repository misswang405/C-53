package com.c53.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Redis 家族撤销补偿任务，与业务撤销在同一数据库事务中提交。
 *
 * <p>不建家族外键，避免业务数据清理级联删除尚未完成的任务；不保存原始令牌。</p>
 */
@Data
@TableName("auth_cleanup_tasks")
public class AuthCleanupTaskEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String familyId;
    private Instant expiresAt;
    private String reason;

    /** PENDING / COMPLETED。失败始终保留 PENDING，不能丢弃撤销任务。 */
    private String status;

    /** 已记录的失败次数，用于退避与并发条件更新。 */
    private Integer attempts;

    private Instant nextRetryAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
