package com.c53.modules.post.repository;

import com.c53.common.ApiException;

/**
 * 动态状态常量。
 *
 * <p>数据库中保存字符串；集中定义避免散落的魔法值。</p>
 */
public final class PostStatus {

    public static final String DRAFT = "DRAFT";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DELETED = "DELETED";

    private PostStatus() {
    }

    /**
     * 解析创建请求中的状态，缺省为立即发布。
     *
     * @param value 客户端状态值
     * @return 合法状态
     */
    public static String parseCreateStatus(String value) {
        if (value == null || value.isBlank()) {
            return PUBLISHED;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case DRAFT, PUBLISHED, SCHEDULED -> normalized;
            default -> throw ApiException.badRequest("VALIDATION_ERROR", "不支持的动态状态");
        };
    }
}
