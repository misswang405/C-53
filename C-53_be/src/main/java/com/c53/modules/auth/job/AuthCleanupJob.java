package com.c53.modules.auth.job;

import com.c53.modules.auth.service.AuthCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期重试 Redis 家族撤销补偿任务。 */
@Component
public class AuthCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

    private final AuthCleanupService cleanupService;

    public AuthCleanupJob(AuthCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${auth.cleanup.scan-interval:PT30S}")
    public void retryPending() {
        try {
            cleanupService.retryPending();
        } catch (RuntimeException exception) {
            // 本轮扫描失败不影响下一轮；单任务失败由服务保留为 PENDING。
            log.error("auth cleanup scan failed", exception);
        }
    }
}
