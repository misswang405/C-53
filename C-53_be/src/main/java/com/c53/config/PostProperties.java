package com.c53.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 发布模块配置。
 *
 * <p>绑定 {@code post.*} 前缀，管理定时范围、调度参数、发布限流、
 * 草稿保留与清理策略。</p>
 */
@Data
@ConfigurationProperties(prefix = "post")
public class PostProperties {

    /** 定时发布配置。 */
    private Schedule schedule = new Schedule();

    /** 发布与自动保存限流。 */
    private Limits limits = new Limits();

    /** 草稿策略。 */
    private Draft draft = new Draft();

    /** 清理策略。 */
    private Cleanup cleanup = new Cleanup();

    /** 基础敏感词表，为空表示不启用。 */
    private List<String> blockedWords = new ArrayList<>();

    /** 定时发布时间范围与后台扫描配置。 */
    @Data
    public static class Schedule {
        /** 最小提前量。 */
        private Duration minLead = Duration.ofMinutes(5);
        /** 最大提前量。 */
        private Duration maxLead = Duration.ofDays(365);
        /** 扫描间隔。 */
        private Duration scanInterval = Duration.ofSeconds(60);
        /** 单批处理数量。 */
        private int batchSize = 200;
        /** 分布式锁有效期。 */
        private Duration lockTtl = Duration.ofSeconds(55);
    }

    /** 发布和自动保存的频率限制。 */
    @Data
    public static class Limits {
        /** 发布次数：每小时。 */
        private int publishPerHour = 10;
        /** 自动保存次数：每分钟。 */
        private int autosavePerMinute = 60;
    }

    /** 草稿数量和保留时长配置。 */
    @Data
    public static class Draft {
        /** 每用户最大草稿数。 */
        private int maxPerUser = 50;
        /** 草稿保留期。 */
        private Duration retention = Duration.ofDays(90);
    }

    /** 删除动态及过期草稿的清理策略。 */
    @Data
    public static class Cleanup {
        /** 删除记录与媒体保留期。 */
        private Duration deleteRetention = Duration.ofDays(30);
        /** 清理任务扫描间隔。 */
        private Duration scanInterval = Duration.ofMinutes(10);
    }
}
