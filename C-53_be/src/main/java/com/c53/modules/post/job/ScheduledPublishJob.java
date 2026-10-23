package com.c53.modules.post.job;

import com.c53.config.PostProperties;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.repository.PostRepository;
import com.c53.modules.topic.service.TopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 定时发布任务。
 *
 * <p>每分钟扫描到期的定时动态。Redis 锁保证多实例同一分钟只有一个实例执行；
 * 即使锁失效或 Redis 不可用，条件更新也保证同一条动态只会被发布一次。</p>
 */
@Component
public class ScheduledPublishJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishJob.class);

    private final PostRepository postRepository;
    private final TopicService topicService;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redis;
    private final PostProperties properties;
    private final Clock clock;

    public ScheduledPublishJob(PostRepository postRepository,
                               TopicService topicService,
                               TransactionTemplate transactionTemplate,
                               StringRedisTemplate redis,
                               PostProperties properties,
                               Clock clock) {
        this.postRepository = postRepository;
        this.topicService = topicService;
        this.transactionTemplate = transactionTemplate;
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 扫描并发布到期动态。
     */
    @Scheduled(fixedDelayString = "${post.schedule.scan-interval:60s}")
    public void publishDuePosts() {
        Instant now = clock.instant();
        if (!acquireLock(now)) {
            return;
        }
        List<PostEntity> due = postRepository.findScheduledDue(now, properties.getSchedule().getBatchSize());
        for (PostEntity post : due) {
            try {
                Instant publishedAt = post.getScheduledAt() == null ? now : post.getScheduledAt();
                transactionTemplate.executeWithoutResult(transaction -> {
                    int updated = postRepository.publishScheduled(post.getId(), publishedAt);
                    if (updated == 1) {
                        topicService.adjustPostCount(post.getTopicId(), 1);
                    }
                });
                log.info("scheduled post published postId={} scheduledAt={} actualAt={} delayMs={}",
                        post.getId(), post.getScheduledAt(), now,
                        post.getScheduledAt() == null ? 0 : now.toEpochMilli() - post.getScheduledAt().toEpochMilli());
            } catch (RuntimeException exception) {
                // 单条失败不影响其他动态，下一轮扫描会重试
                log.error("scheduled post publish failed postId={}", post.getId(), exception);
            }
        }
    }

    /**
     * 抢占当前分钟的执行权。Redis 不可用时放行，依赖条件更新兜底。
     */
    private boolean acquireLock(Instant now) {
        String key = "post:schedule:lock:" + (now.getEpochSecond() / 60);
        Duration ttl = properties.getSchedule().getLockTtl();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException exception) {
            log.warn("schedule lock unavailable, continue without lock", exception);
            return true;
        }
    }
}
