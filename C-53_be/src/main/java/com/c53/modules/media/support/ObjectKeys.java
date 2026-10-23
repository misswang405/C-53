package com.c53.modules.media.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * OSS 对象 Key 生成。
 *
 * <p>客户端传入的文件名不参与路径，避免路径穿越；所有片段均经过字符白名单过滤。</p>
 */
public final class ObjectKeys {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd")
            .withZone(ZoneOffset.UTC);

    private ObjectKeys() {
    }

    /**
     * 正式对象 Key：{@code {env}/{image|video}/{yyyy}/{MM}/{dd}/{ownerId}/{mediaId}.{ext}}。
     *
     * @param env      环境
     * @param mediaType IMAGE/VIDEO
     * @param now      当前时间
     * @param ownerId  上传者
     * @param mediaId  媒体 ID
     * @param mime     MIME
     * @return 对象 Key
     */
    public static String finalKey(String env, String mediaType, Instant now,
                                  String ownerId, String mediaId, String mime) {
        return segment(env) + "/" + folder(mediaType) + "/" + DAY.format(now)
                + "/" + segment(ownerId) + "/" + segment(mediaId) + "." + extension(mime);
    }

    /**
     * 临时对象 Key：{@code {env}/temp/{ownerId}/{uploadId}/source.{ext}}。
     *
     * @param env      环境
     * @param ownerId  上传者
     * @param uploadId 上传 ID
     * @param mime     MIME
     * @return 对象 Key
     */
    public static String tempKey(String env, String ownerId, String uploadId, String mime) {
        return segment(env) + "/temp/" + segment(ownerId) + "/" + segment(uploadId)
                + "/source." + extension(mime);
    }

    /**
     * MIME 对应的扩展名。
     *
     * @param mime MIME
     * @return 扩展名，未知时返回 bin
     */
    public static String extension(String mime) {
        return switch (mime == null ? "" : mime.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            default -> "bin";
        };
    }

    /**
     * 临时 Key 的前缀，用于 STS 会话策略限定范围。
     *
     * @param env      环境
     * @param ownerId  上传者
     * @param uploadId 上传 ID
     * @return 前缀，以 {@code /} 结尾
     */
    public static String tempPrefix(String env, String ownerId, String uploadId) {
        return segment(env) + "/temp/" + segment(ownerId) + "/" + segment(uploadId) + "/";
    }

    private static String folder(String mediaType) {
        return "VIDEO".equalsIgnoreCase(mediaType) ? "video" : "image";
    }

    /**
     * 只保留字母、数字、下划线和连字符，防御路径穿越。
     */
    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
