package com.c53.modules.topic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 话题实体，对应 {@code topics}。
 *
 * <p>本期只提供发布功能依赖的只读能力与已发布计数维护，
 * 完整话题管理由后续话题模块负责。</p>
 */
@Data
@TableName("topics")
public class TopicEntity {

    /** 主键，{@code tpc_} + ULID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 话题名，唯一。 */
    private String name;

    /** 状态：ACTIVE/DISABLED。 */
    private String status;

    /** 已发布动态数。 */
    private Long postCount;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;
}
