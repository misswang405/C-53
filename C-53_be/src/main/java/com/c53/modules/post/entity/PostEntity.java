package com.c53.modules.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 动态实体，对应 {@code posts}。
 *
 * <p>正文存 MySQL；图片和视频只保存媒体类型，具体对象由
 * {@code media_assets} 维护。软删除通过 {@code status=DELETED}
 * 与 {@code deleted_at} 表达，不使用 MyBatis-Plus 逻辑删除。</p>
 */
@Data
@TableName("posts")
public class PostEntity {

    /** 主键，{@code pst_} + ULID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 作者用户 ID。 */
    private String authorId;

    /** 状态：DRAFT/SCHEDULED/PUBLISHED/DELETED。 */
    private String status;

    /** 纯文本正文。 */
    private String content;

    /** 话题 ID。 */
    private String topicId;

    /** 媒体类型：IMAGE/VIDEO，无媒体为 null。 */
    private String mediaType;

    /** 点赞数。 */
    private Long likeCount;

    /** 评论与回复总数。 */
    private Long commentCount;

    /** 计划发布时间。 */
    private Instant scheduledAt;

    /** 公开时间。 */
    private Instant publishedAt;

    /** 最近编辑时间。 */
    private Instant editedAt;

    /** 乐观锁版本。 */
    private Integer version;

    /** 创建幂等键。 */
    private String clientRequestId;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;

    /** 软删除时间。 */
    private Instant deletedAt;
}
