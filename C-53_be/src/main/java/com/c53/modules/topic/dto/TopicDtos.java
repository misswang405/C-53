package com.c53.modules.topic.dto;

/**
 * 话题接口模型。
 */
public final class TopicDtos {

    private TopicDtos() {
    }

    /**
     * 话题摘要。
     *
     * @param id        话题 ID
     * @param name      话题名
     * @param postCount 已发布动态数
     */
    public record TopicSummary(String id, String name, long postCount) {
    }
}
