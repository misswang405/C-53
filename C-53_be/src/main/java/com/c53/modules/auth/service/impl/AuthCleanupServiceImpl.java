package com.c53.modules.auth.service.impl;

import com.c53.common.support.IdGenerator;
import com.c53.modules.auth.entity.AuthCleanupTaskEntity;
import com.c53.modules.auth.mapper.AuthCleanupTaskMapper;
import com.c53.modules.auth.mapper.AuthRefreshTokenMapper;
import com.c53.modules.auth.redis.RefreshTokenStore;
import com.c53.modules.auth.service.AuthCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Redis 家族撤销补偿服务。
 *
 * <p>不持有跨 Redis 调用的数据库事务或分布式锁。多个实例可处理同一任务，
 * 重复家族吊销和摘要删除是幂等的，失败延期不能覆盖已完成状态。</p>
 */
@Service
public class AuthCleanupServiceImpl implements AuthCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupServiceImpl.class);

    private static final int BATCH_SIZE = 100;
    private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private final AuthCleanupTaskMapper taskMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    public AuthCleanupServiceImpl(AuthCleanupTaskMapper taskMapper,
                                  AuthRefreshTokenMapper refreshTokenMapper,
                                  RefreshTokenStore refreshTokenStore,
                                  Clock clock) {
        this.taskMapper = taskMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.refreshTokenStore = refreshTokenStore;
        this.clock = clock;
    }

    /**
     * 仅执行数据库 INSERT，事务边界由调用者决定；调用失败必须向上抛出。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String familyId, Instant expiresAt, String reason) {
        requireAscii(familyId, 40, "familyId");
        requireAscii(reason, 32, "reason");
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        Instant now = clock.instant();
        AuthCleanupTaskEntity task = new AuthCleanupTaskEntity();
        task.setId(IdGenerator.newId("acl"));
        task.setFamilyId(familyId);
        task.setExpiresAt(expiresAt);
        task.setReason(reason);
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setNextRetryAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (taskMapper.insert(task) != 1) {
            throw new IllegalStateException("auth cleanup task was not inserted");
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void retryPending() {
        List<AuthCleanupTaskEntity> tasks = taskMapper.selectPending(clock.instant(), BATCH_SIZE);
        for (AuthCleanupTaskEntity task : tasks) {
            retryOne(task);
        }
    }

    private void retryOne(AuthCleanupTaskEntity task) {
        Instant now = clock.instant();
        try {
            Duration ttl = Duration.between(now, task.getExpiresAt());
            if (ttl.isNegative() || ttl.isZero()) {
                // Redis 至少需要一个短暂的撤销标记窗口，避免已过期任务阻塞审计摘要删除。
                ttl = Duration.ofSeconds(1);
            }
            List<String> hashes = refreshTokenMapper.selectHashesByFamily(task.getFamilyId());
            refreshTokenStore.revokeFamily(task.getFamilyId(), task.getReason(), ttl, hashes);
            taskMapper.markCompleted(task.getId(), clock.instant());
        } catch (RuntimeException exception) {
            Instant failedAt = clock.instant();
            Instant nextRetryAt = failedAt.plus(retryDelay(task.getAttempts()));
            // 不记录令牌摘要或依赖异常消息，避免把敏感 Redis Key 带入日志。
            log.warn("auth cleanup failed taskId={} familyId={} attempts={} errorType={}",
                    task.getId(), task.getFamilyId(), task.getAttempts(), exception.getClass().getSimpleName());
            try {
                taskMapper.deferRetry(task.getId(), task.getAttempts(), nextRetryAt, failedAt);
            } catch (RuntimeException retryException) {
                // 延期写库失败时原任务仍为 PENDING，下一轮可以重新扫描，且不阻断同批任务。
                log.error("auth cleanup retry update failed taskId={} errorType={}",
                        task.getId(), retryException.getClass().getSimpleName());
            }
        }
    }

    private static void requireAscii(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(character -> character < 32 || character > 126)) {
            throw new IllegalArgumentException(name + " must be printable ASCII within " + maxLength + " characters");
        }
    }

    private static Duration retryDelay(Integer attempts) {
        int count = attempts == null ? 0 : Math.max(0, attempts);
        long multiplier = 1L << Math.min(count, 16);
        long seconds = Math.min(MAX_RETRY_DELAY.toSeconds(), MIN_RETRY_DELAY.toSeconds() * multiplier);
        return Duration.ofSeconds(seconds);
    }
}
