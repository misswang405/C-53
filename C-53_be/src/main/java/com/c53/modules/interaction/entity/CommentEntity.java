package com.c53.modules.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 动态评论实体，支持一级评论和二级回复。
 */
@Data
@TableName("comments")
public class CommentEntity {

    /** 评论 ID。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 所属动态 ID。 */
    private String postId;
    /** 评论作者 ID。 */
    private String authorId;
    /** 父评论 ID；为空表示一级评论。 */
    private String parentId;
    /** 评论正文。 */
    private String content;
    /** 评论状态。 */
    private String status;
    /** 创建时间。 */
    private Instant createdAt;
    /** 最近更新时间。 */
    private Instant updatedAt;
    /** 软删除时间。 */
    private Instant deletedAt;
}
