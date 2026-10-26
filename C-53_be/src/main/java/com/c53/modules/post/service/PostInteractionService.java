package com.c53.modules.post.service;

import com.c53.modules.post.entity.PostEntity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 为互动模块提供动态查询和计数更新能力。
 */
public interface PostInteractionService {

    /** 查询单条已发布且未删除的动态。 */
    Optional<PostEntity> findPublished(String postId);

    /** 批量查询已发布且未删除的动态。 */
    Map<String, PostEntity> findPublished(Collection<String> postIds);

    /** 原子增加动态点赞数。 */
    int incrementLike(String postId);

    /** 原子减少动态点赞数。 */
    int decrementLike(String postId);

    /** 原子增加动态评论数。 */
    int incrementComment(String postId);
}
