package com.c53.modules.media.support;

import com.c53.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 文件魔数校验。
 *
 * <p>只信任文件头，不信任扩展名和客户端声明的 Content-Type。
 * 支持 JPEG、PNG、WebP 图片与 MP4 视频。</p>
 */
public final class MediaValidator {

    /** 允许的 MP4 brand。 */
    private static final String[] MP4_BRANDS = {"isom", "iso2", "mp41", "mp42", "avc1"};

    private MediaValidator() {
    }

    /**
     * 识别结果。
     *
     * @param mediaType IMAGE/VIDEO
     * @param mime      MIME
     */
    public record DetectedMedia(String mediaType, String mime) {
    }

    /**
     * 根据文件头识别媒体类型。
     *
     * @param header 文件头，至少 12 字节
     * @return 识别结果
     */
    public static DetectedMedia detect(byte[] header) {
        if (isJpeg(header)) {
            return new DetectedMedia("IMAGE", "image/jpeg");
        }
        if (isPng(header)) {
            return new DetectedMedia("IMAGE", "image/png");
        }
        if (isWebp(header)) {
            return new DetectedMedia("IMAGE", "image/webp");
        }
        if (isMp4(header)) {
            return new DetectedMedia("VIDEO", "video/mp4");
        }
        throw unsupported();
    }

    /**
     * 校验识别结果与声明类型一致。
     *
     * @param detected     识别结果
     * @param declaredType 客户端声明类型
     */
    public static void requireMatches(DetectedMedia detected, String declaredType) {
        String expected = normalizeType(declaredType);
        if (expected == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "mediaType 只能是 IMAGE 或 VIDEO");
        }
        if (!expected.equals(detected.mediaType())) {
            throw unsupported();
        }
    }

    /**
     * 规范化媒体类型。
     *
     * @param value 原始值
     * @return IMAGE/VIDEO，非法时返回 null
     */
    public static String normalizeType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return "IMAGE".equals(normalized) || "VIDEO".equals(normalized) ? normalized : null;
    }

    /**
     * 构造 415 不支持的媒体类型异常。
     *
     * @return 业务异常
     */
    public static ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "仅支持 JPEG、PNG、WebP 图片和 MP4 视频");
    }

    private static boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] header) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (header.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (header[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private static boolean isMp4(byte[] header) {
        if (header.length < 12 || header[4] != 'f' || header[5] != 't' || header[6] != 'y' || header[7] != 'p') {
            return false;
        }
        String brand = new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
        for (String allowed : MP4_BRANDS) {
            if (allowed.equals(brand)) {
                return true;
            }
        }
        return false;
    }
}
