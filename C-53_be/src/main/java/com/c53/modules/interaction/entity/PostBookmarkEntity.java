package com.c53.modules.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 动态收藏关系实体。
 */
@Data
@TableName("post_bookmarks")
public class PostBookmarkEntity {

    /** 收藏记录 ID。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 被收藏的动态 ID。 */
    private String postId;
    /** 收藏用户 ID。 */
    private String userId;
    /** 收藏时间。 */
    private Instant bookmarkedAt;
}
