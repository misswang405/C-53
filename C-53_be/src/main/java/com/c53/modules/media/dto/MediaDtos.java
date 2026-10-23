package com.c53.modules.media.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * 媒体接口的请求与响应模型。
 */
public final class MediaDtos {

    private MediaDtos() {
    }

    /**
     * 上传能力配置，供前端决定走服务端还是直传通道。
     */
    public record MediaConfigResponse(
            long serverUploadMaxBytes,
            long imageMaxBytes,
            long videoMaxBytes,
            long videoMaxDurationSeconds,
            int imageMaxCount,
            int videoMaxCount,
            List<String> allowedImageTypes,
            List<String> allowedVideoTypes,
            long ticketTtlSeconds,
            long partSizeBytes) {
    }

    /**
     * 申请直传凭证。
     *
     * @param fileName    原始文件名，仅用于日志
     * @param contentType 客户端声明的 MIME
     * @param sizeBytes   声明大小
     * @param mediaType   媒体类型，仅支持 VIDEO
     */
    public record UploadTicketRequest(
            String fileName,
            String contentType,
            @NotNull Long sizeBytes,
            @NotNull String mediaType) {
    }

    /**
     * 直传凭证与临时路径。
     */
    public record TicketResponse(
            String mediaId,
            String uploadId,
            String accessKeyId,
            String accessKeySecret,
            String securityToken,
            Instant expiration,
            String region,
            String endpoint,
            String bucket,
            String objectKey,
            long partSizeBytes,
            String status) {
    }

    /**
     * 直传完成确认。
     *
     * @param etag      客户端拿到的 ETag，可为空
     * @param sizeBytes 客户端声明的实际大小，可为空
     */
    public record ConfirmRequest(String etag, Long sizeBytes) {
    }

    /**
     * 媒体对外视图。{@code url} 是短期签名地址，不落库。
     */
    public record MediaView(
            String mediaId,
            String mediaType,
            String url,
            Integer width,
            Integer height,
            Long durationMs,
            Long sizeBytes,
            int sortOrder) {
    }
}
