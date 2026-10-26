package com.c53.modules.interaction.dto;

import com.c53.modules.auth.service.UserSummaryService.UserSummary;
import com.c53.modules.post.dto.PostDtos.PostResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * 互动模块的请求和响应 record 集合。
 */
public final class InteractionDtos {

    private InteractionDtos() {
    }

    /** 点赞状态及动态当前点赞数。 */
    public record LikeResponse(boolean active, long count) {
    }

    /** 收藏状态。 */
    public record BookmarkResponse(boolean active) {
    }

    /** 创建评论或回复的请求。 */
    public record CommentRequest(@NotBlank String content) {
    }

    /** 分享事件请求，channel 表示分享渠道。 */
    public record ShareEventRequest(@NotNull String channel) {
    }

    /** 评论及其一级回复的展示模型。 */
    public record CommentView(
            String id,
            UserSummary author,
            String content,
            Instant createdAt,
            List<CommentView> replies,
            boolean repliesTruncated) {
    }

    /** 评论分页响应。 */
    public record CommentListResponse(List<CommentView> items, String nextCursor) {
    }

    /** 收藏动态分页响应。 */
    public record BookmarkListResponse(List<PostResponse> items, String nextCursor) {
    }
}
