package com.c53.modules.interaction.support;

import com.c53.common.ApiException;

/**
 * 互动内容和分页参数的业务规则。
 */
public final class InteractionRules {

    /** 评论允许的最大 Unicode 码点数。 */
    public static final int MAX_COMMENT_CODE_POINTS = 500;
    /** 单次评论列表查询的最大条数。 */
    public static final int MAX_COMMENT_LIMIT = 50;
    /** 单条一级评论最多展示的回复数。 */
    public static final int MAX_REPLIES_PER_COMMENT = 20;

    private InteractionRules() {
    }

    /** 统一换行并去除首尾空白，拒绝非法控制字符，按 Unicode 码点限制长度。 */
    public static String normalizeComment(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("CONTENT_EMPTY", "评论内容不能为空");
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').strip();
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            boolean invalidControl = (codePoint < 0x20 && codePoint != '\n' && codePoint != '\t')
                    || codePoint == 0x7F;
            if (invalidControl) {
                throw ApiException.badRequest("VALIDATION_ERROR", "评论包含非法控制字符");
            }
            index += Character.charCount(codePoint);
        }
        int length = text.codePointCount(0, text.length());
        if (length == 0) {
            throw ApiException.badRequest("CONTENT_EMPTY", "评论内容不能为空");
        }
        if (length > MAX_COMMENT_CODE_POINTS) {
            throw ApiException.badRequest("CONTENT_TOO_LONG", "评论不能超过 500 个字符");
        }
        return text;
    }

    /** 规范化分享渠道，并只接受已开放的渠道标识。 */
    public static String normalizeChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("INVALID_SHARE_CHANNEL", "分享渠道不支持");
        }
        String channel = raw.trim().toUpperCase();
        if (!switch (channel) {
            case "WECHAT", "QQ", "DOUYIN" -> true;
            default -> false;
        }) {
            throw ApiException.badRequest("INVALID_SHARE_CHANNEL", "分享渠道不支持");
        }
        return channel;
    }
}
