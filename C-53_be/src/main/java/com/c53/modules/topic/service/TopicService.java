package com.c53.modules.topic.service;

import com.c53.modules.topic.dto.TopicDtos.TopicSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 话题只读能力与计数维护。
 */
public interface TopicService {

    /**
     * 校验话题存在且启用。
     *
     * @param topicId 话题 ID
     * @return 话题摘要
     */
    TopicSummary requireActive(String topicId);

    /**
     * 批量查询话题摘要。
     *
     * @param topicIds 话题 ID 集合
     * @return ID 到摘要的映射
     */
    Map<String, TopicSummary> findByIds(Collection<String> topicIds);

    /**
     * 热门话题。
     *
     * @param limit 最大条数
     * @return 话题列表
     */
    List<TopicSummary> trending(int limit);

    /**
     * 调整已发布动态数，必须在调用方事务内执行。
     *
     * @param topicId 话题 ID
     * @param delta   增量，可为负
     */
    void adjustPostCount(String topicId, int delta);
}
