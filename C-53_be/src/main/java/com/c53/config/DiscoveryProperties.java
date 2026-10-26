package com.c53.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 社区与发现模块配置。
 *
 * <p>绑定 {@code discovery.*} 前缀，管理在线人数窗口、信息流候选与限流、
 * 推荐创作者候选与缓存。</p>
 */
@Data
@ConfigurationProperties(prefix = "discovery")
public class DiscoveryProperties {

    /** 在线人数配置。 */
    private Online online = new Online();

    /** 信息流配置。 */
    private Feed feed = new Feed();

    /** 推荐创作者配置。 */
    private Creator creator = new Creator();

    /** 在线状态和在线人数统计配置。 */
    @Data
    public static class Online {
        /** 在线判定窗口。 */
        private Duration window = Duration.ofMinutes(5);
        /** 同一用户写入节流间隔。 */
        private Duration touchInterval = Duration.ofSeconds(60);
        /** 在线人数上限，超过视为异常数据。 */
        private long maxCount = 1_000_000L;
    }

    /** 信息流候选、分页和访问频率配置。 */
    @Data
    public static class Feed {
        /** 推荐候选时间窗口。 */
        private Duration recommendedWindow = Duration.ofDays(7);
        /** 推荐候选上限。 */
        private int candidateLimit = 500;
        /** 同一作者连续出现上限。 */
        private int maxAuthorStreak = 2;
        /** 默认返回条数。 */
        private int defaultLimit = 20;
        /** 单次最大返回条数。 */
        private int maxLimit = 50;
        /** 匿名请求每分钟上限。 */
        private int anonymousPerMinute = 60;
        /** 登录请求每分钟上限。 */
        private int userPerMinute = 120;
    }

    /** 推荐创作者候选、缓存和限流配置。 */
    @Data
    public static class Creator {
        /** 候选用户上限。 */
        private int candidateLimit = 100;
        /** 返回条数。 */
        private int resultLimit = 6;
        /** 候选池缓存有效期。 */
        private Duration cacheTtl = Duration.ofMinutes(5);
        /** 活跃统计窗口。 */
        private Duration activeWindow = Duration.ofDays(30);
        /** 每分钟请求上限。 */
        private int perMinutePerIp = 30;
    }
}
