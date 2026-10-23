package com.c53.modules.topic.service.impl;

import com.c53.common.ApiException;
import com.c53.modules.topic.dto.TopicDtos.TopicSummary;
import com.c53.modules.topic.entity.TopicEntity;
import com.c53.modules.topic.repository.TopicRepository;
import com.c53.modules.topic.service.TopicService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 话题服务实现。
 */
@Service
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;

    public TopicServiceImpl(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Override
    public TopicSummary requireActive(String topicId) {
        if (topicId == null || topicId.isBlank()) {
            throw ApiException.badRequest("TOPIC_REQUIRED", "请选择话题");
        }
        TopicEntity topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "TOPIC_NOT_FOUND", "话题不存在"));
        if (!"ACTIVE".equalsIgnoreCase(topic.getStatus())) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "TOPIC_NOT_FOUND", "话题已停用");
        }
        return toSummary(topic);
    }

    @Override
    public Map<String, TopicSummary> findByIds(Collection<String> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TopicSummary> result = new HashMap<>();
        for (TopicEntity topic : topicRepository.findByIds(topicIds)) {
            result.put(topic.getId(), toSummary(topic));
        }
        return result;
    }

    @Override
    public List<TopicSummary> trending(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return topicRepository.findTrending(size).stream().map(this::toSummary).toList();
    }

    @Override
    public void adjustPostCount(String topicId, int delta) {
        if (topicId != null) {
            topicRepository.adjustPostCount(topicId, delta);
        }
    }

    private TopicSummary toSummary(TopicEntity topic) {
        long count = topic.getPostCount() == null ? 0 : topic.getPostCount();
        return new TopicSummary(topic.getId(), topic.getName(), count);
    }
}
