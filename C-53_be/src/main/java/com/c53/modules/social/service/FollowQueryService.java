package com.c53.modules.social.service;

import java.util.Set;

/**
 * 关注关系只读查询。
 *
 * <p>信息流与推荐创作者依赖关注关系，但 3.6 关注模块尚未实现。
 * 本接口先冻结边界，本期由 {@code NoopFollowQueryService} 返回空结果，
 * 关注模块上线后只替换实现，不改调用方。</p>
 */
public interface FollowQueryService {

    /**
     * 查询用户关注的所有用户 ID。
     *
     * @param userId 用户 ID
     * @return 被关注用户 ID 集合，无数据时返回空集合
     */
    Set<String> findFollowingIds(String userId);

    /**
     * 查询两个用户之间的关系。
     *
     * @param viewerId 当前查看者 ID
     * @param targetId 目标用户 ID
     * @return 关注关系，未知时返回 {@link Relationship#NONE}
     */
    Relationship relationship(String viewerId, String targetId);

    /**
     * 关注关系。
     */
    enum Relationship {
        /** 我关注了对方。 */
        FOLLOWING,
        /** 对方关注了我。 */
        FOLLOWED_BY,
        /** 互相关注。 */
        MUTUAL,
        /** 无关系或未登录。 */
        NONE
    }
}
