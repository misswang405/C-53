package com.c53.modules.post.dto;

import com.c53.modules.auth.service.UserSummaryService.UserSummary;
import com.c53.modules.media.dto.MediaDtos.MediaView;
import com.c53.modules.topic.dto.TopicDtos.TopicSummary;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * 发布接口的请求与响应模型。
 */
public final class PostDtos {

    private PostDtos() {
    }

    /**
     * 创建动态。
     *
     * @param content         正文
     * @param topicId         话题
     * @param mediaIds        媒体 ID 顺序
     * @param imageIds        旧字段兼容
     * @param status          DRAFT/PUBLISHED/SCHEDULED，缺省 PUBLISHED
     * @param scheduledAt     计划时间
     * @param clientRequestId 创建幂等键
     */
    public record CreatePostRequest(
            String content,
            String topicId,
            List<String> mediaIds,
            List<String> imageIds,
            String status,
            Instant scheduledAt,
            String clientRequestId) {
    }

    /**
     * 编辑动态，必须携带当前版本号。
     */
    public record UpdatePostRequest(
            String content,
            String topicId,
            List<String> mediaIds,
            List<String> imageIds,
            @NotNull Integer version) {
    }

    /**
     * 设置或修改定时发布。
     */
    public record ScheduleRequest(@NotNull Instant scheduledAt) {
    }

    /**
     * 动态完整视图。
     */
    public record PostResponse(
            String id,
            UserSummary author,
            TopicSummary topic,
            String content,
            String summary,
            String mediaType,
            List<MediaView> media,
            String status,
            Instant scheduledAt,
            Instant publishedAt,
            Instant editedAt,
            Instant createdAt,
            int version,
            boolean ownedByMe) {
    }

    /**
     * 我的发布列表。
     */
    public record PostListResponse(List<PostResponse> items, String nextCursor) {
    }
}
