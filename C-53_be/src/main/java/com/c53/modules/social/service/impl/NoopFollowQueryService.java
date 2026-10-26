package com.c53.modules.social.service.impl;

import com.c53.modules.social.service.FollowQueryService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 关注关系空实现。
 *
 * <p>3.6 关注模块上线前的临时实现：关注流返回空列表，推荐创作者不做已关注过滤，
 * 所有关系均为 {@link Relationship#NONE}。届时删除本类，由真实实现替换。</p>
 */
@Service
public class NoopFollowQueryService implements FollowQueryService {

    @Override
    public Set<String> findFollowingIds(String userId) {
        return Set.of();
    }

    @Override
    public Relationship relationship(String viewerId, String targetId) {
        return Relationship.NONE;
    }
}
