package com.c53.modules.post.support;

import com.c53.common.ApiException;

import java.util.Locale;

/**
 * 信息流范围。
 *
 * <p>前端三个标签分别对应 {@code RECOMMENDED}、{@code FOLLOWING}、{@code LATEST}。
 * 缺省按推荐流处理，非法值返回 400，避免静默返回错误内容。</p>
 */
public enum FeedScope {

    /** 为你推荐。 */
    RECOMMENDED,

    /** 正在关注，必须登录。 */
    FOLLOWING,

    /** 最新发布。 */
    LATEST;

    /**
     * 解析前端传入的 scope。
     *
     * @param raw 原始值，可为空
     * @return 枚举值，缺省为 {@link #RECOMMENDED}
     * @throws ApiException 取值非法时抛出 400
     */
    public static FeedScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RECOMMENDED;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("VALIDATION_ERROR", "信息流范围不合法");
        }
    }
}
