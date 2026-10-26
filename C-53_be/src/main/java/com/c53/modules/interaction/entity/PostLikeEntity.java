package com.c53.modules.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 动态点赞关系实体。
 */
@Data
@TableName("post_likes")
public class PostLikeEntity {

    /** 点赞记录 ID。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 被点赞的动态 ID。 */
    private String postId;
    /** 点赞用户 ID。 */
    private String userId;
    /** 点赞时间。 */
    private Instant createdAt;
}
