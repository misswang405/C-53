package com.c53.modules.topic.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.modules.topic.entity.TopicEntity;
import com.c53.modules.topic.mapper.TopicMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 话题数据访问。
 */
@Repository
public class TopicRepository {

    private final TopicMapper mapper;

    public TopicRepository(TopicMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<TopicEntity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<TopicEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<TopicEntity>lambdaQuery()
                .in(TopicEntity::getId, ids));
    }

    public List<TopicEntity> findTrending(int limit) {
        return mapper.selectList(Wrappers.<TopicEntity>lambdaQuery()
                .eq(TopicEntity::getStatus, "ACTIVE")
                .orderByDesc(TopicEntity::getPostCount)
                .orderByAsc(TopicEntity::getId)
                .last("LIMIT " + limit));
    }

    /**
     * 调整已发布动态数，使用 SQL 保证并发安全且不出现负数。
     */
    public int adjustPostCount(String topicId, int delta) {
        return mapper.update(null, Wrappers.<TopicEntity>lambdaUpdate()
                .setSql("post_count = GREATEST(post_count + (" + delta + "), 0)")
                .eq(TopicEntity::getId, topicId));
    }
}
