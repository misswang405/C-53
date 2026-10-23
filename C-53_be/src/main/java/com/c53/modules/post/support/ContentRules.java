package com.c53.modules.post.support;

import com.c53.common.ApiException;

/**
 * 正文规范化与校验。
 *
 * <p>正文是纯文本：统一换行符、折叠多余空行、去掉首尾空白、
 * 拒绝控制字符，并按 Unicode 码点计数（emoji 按 1 个字符计算）。</p>
 */
public final class ContentRules {

    /** 正文最大码点数。 */
    public static final int MAX_CODE_POINTS = 600;

    /** 列表摘要最大码点数。 */
    private static final int SUMMARY_CODE_POINTS = 120;

    private ContentRules() {
    }

    /**
     * 规范化正文。
     *
     * @param raw 原始正文
     * @return 规范化后的正文，非 null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        // 连续 3 个及以上换行折叠为 1 个空行
        text = text.replaceAll("\n{3,}", "\n\n");
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            boolean invalidControl = (codePoint < 0x20 && codePoint != '\n' && codePoint != '\t')
                    || codePoint == 0x7F;
            if (invalidControl) {
                throw ApiException.badRequest("VALIDATION_ERROR", "正文包含非法控制字符");
            }
            index += Character.charCount(codePoint);
        }
        return text.strip();
    }

    /**
     * 计算码点长度。
     *
     * @param text 正文
     * @return 码点数
     */
    public static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    /**
     * 草稿校验：允许为空，但不得超过上限。
     *
     * @param content 已规范化的正文
     */
    public static void requireDraft(String content) {
        if (length(content) > MAX_CODE_POINTS) {
            throw ApiException.badRequest("CONTENT_TOO_LONG", "正文不能超过 600 个字符");
        }
    }

    /**
     * 发布校验：必须非空且不超长。
     *
     * @param content 已规范化的正文
     */
    public static void requirePublish(String content) {
        if (content == null || content.isEmpty()) {
            throw ApiException.badRequest("CONTENT_EMPTY", "正文不能为空");
        }
        if (length(content) > MAX_CODE_POINTS) {
            throw ApiException.badRequest("CONTENT_TOO_LONG", "正文不能超过 600 个字符");
        }
    }

    /**
     * 生成列表摘要。
     *
     * @param content 正文
     * @return 最多 120 个码点的摘要
     */
    public static String summary(String content) {
        if (content == null) {
            return "";
        }
        int length = content.codePointCount(0, content.length());
        if (length <= SUMMARY_CODE_POINTS) {
            return content;
        }
        int end = content.offsetByCodePoints(0, SUMMARY_CODE_POINTS);
        return content.substring(0, end) + "…";
    }
}
