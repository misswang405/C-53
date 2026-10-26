package com.c53.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 互动模块的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "interaction")
public class InteractionProperties {

    /** 各类互动接口的限流配置。 */
    private Limits limits = new Limits();

    /** 互动接口按分钟划分的请求上限。 */
    @Data
    public static class Limits {
        /** 单用户每分钟点赞上限。 */
        private int likePerMinute = 120;
        /** 单用户每分钟收藏上限。 */
        private int bookmarkPerMinute = 120;
        /** 单用户每分钟评论上限。 */
        private int commentPerMinute = 30;
        /** 单 IP 每分钟评论读取上限。 */
        private int commentsReadPerMinute = 120;
        /** 单用户每分钟分享上限。 */
        private int sharePerMinute = 60;
        /** 单用户每分钟收藏列表读取上限。 */
        private int bookmarksReadPerMinute = 60;
    }
}
