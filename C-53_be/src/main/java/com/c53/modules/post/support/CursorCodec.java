package com.c53.modules.post.support;

import com.c53.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 列表游标编解码。
 *
 * <p>游标由「时间戳毫秒 + 记录 ID」组成，Base64 URL 编码后返回给前端。
 * 使用时间与 ID 的复合条件分页，避免深分页和同时间记录的重复/遗漏。</p>
 */
public final class CursorCodec {

    private CursorCodec() {
    }

    /**
     * 游标内容。
     *
     * @param time 排序时间
     * @param id   记录 ID
     */
    public record Cursor(Instant time, String id) {
    }

    /**
     * 编码游标。
     *
     * @param time 排序时间
     * @param id   记录 ID
     * @return Base64 URL 字符串
     */
    public static String encode(Instant time, String id) {
        String raw = time.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码游标。
     *
     * @param value Base64 URL 字符串
     * @return 游标内容；输入为空时返回 null
     */
    public static Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                    raw.substring(separator + 1));
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("VALIDATION_ERROR", "分页游标不合法");
        }
    }
}
