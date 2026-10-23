package com.c53.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * 定时任务基础配置。
 *
 * <p>开启 {@code @Scheduled}，并提供统一的 UTC 时钟。
 * 业务任务通过注入 {@link Clock} 获取时间，避免多实例时钟偏差，
 * 也便于单元测试固定时间。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 统一 UTC 时钟。
     *
     * @return 系统 UTC 时钟
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
