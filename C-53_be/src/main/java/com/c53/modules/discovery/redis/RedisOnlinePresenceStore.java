package com.c53.modules.discovery.redis;

import com.c53.common.presence.OnlinePresenceRecorder;
import com.c53.config.DiscoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的在线人数统计。
 *
 * <p>使用 ZSET 保存用户 ID 与最近活跃时间，查询时统计窗口内的成员数。
 * 同一用户按 {@code touch-interval} 节流，节流标记本身也放在 Redis，
 * 避免多实例本地状态不一致或内存持续增长。所有异常静默降级为 0。</p>
 */
@Component
public class RedisOnlinePresenceStore implements OnlinePresenceRecorder {

    private static final Logger log = LoggerFactory.getLogger(RedisOnlinePresenceStore.class);

    /** 在线用户 ZSET。 */
    private static final String ONLINE_KEY = "discovery:online";

    /** 写入节流键前缀。 */
    private static final String TOUCH_PREFIX = "discovery:online:touch:";

    private final StringRedisTemplate redis;
    private final DiscoveryProperties properties;

    public RedisOnlinePresenceStore(StringRedisTemplate redis, DiscoveryProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void touch(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            Boolean first = redis.opsForValue().setIfAbsent(
                    TOUCH_PREFIX + userId, "1", properties.getOnline().getTouchInterval());
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
            long now = System.currentTimeMillis();
            redis.opsForZSet().add(ONLINE_KEY, userId, now);
            long cutoff = now - properties.getOnline().getWindow().toMillis();
            redis.opsForZSet().removeRangeByScore(ONLINE_KEY, 0, cutoff);
        } catch (RuntimeException exception) {
            log.warn("online presence touch failed userId={}", userId, exception);
        }
    }

    @Override
    public long count() {
        try {
            long now = System.currentTimeMillis();
            long cutoff = now - properties.getOnline().getWindow().toMillis();
            Long count = redis.opsForZSet().count(ONLINE_KEY, cutoff, Double.POSITIVE_INFINITY);
            long value = count == null ? 0 : count;
            if (value < 0 || value > properties.getOnline().getMaxCount()) {
                log.warn("online count abnormal value={}", value);
                return 0;
            }
            return value;
        } catch (RuntimeException exception) {
            log.warn("online count unavailable", exception);
            return 0;
        }
    }
}
