package com.c53.modules.interaction.service;

import java.util.Collection;
import java.util.Map;

/**
 * 为动态展示查询聚合后的互动摘要。
 */
public interface InteractionQueryService {

    /** 单条动态的点赞、收藏和评论摘要。 */
    record InteractionSummary(long likeCount, boolean likedByMe,
                             boolean bookmarkedByMe, long commentCount) {
    }

    /** 批量查询动态摘要，并按动态 ID 返回。 */
    Map<String, InteractionSummary> findSummaries(Collection<String> postIds, String viewerId);
}
