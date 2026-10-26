package com.c53.modules.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 动态分享统计事件实体。
 */
@Data
@TableName("post_share_events")
public class ShareEventEntity {

    /** 分享事件 ID。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 被分享的动态 ID。 */
    private String postId;
    /** 分享用户 ID。 */
    private String userId;
    /** 分享渠道。 */
    private String channel;
    /** 事件创建时间。 */
    private Instant createdAt;
}
