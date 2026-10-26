package com.c53.modules.discovery.redis;

import com.c53.config.DiscoveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线人数写入节流与故障降级。
 */
class OnlinePresenceStoreTest {

    private static final String ONLINE_KEY = "discovery:online";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private RedisOnlinePresenceStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForZSet()).thenReturn(zSetOperations);
        store = new RedisOnlinePresenceStore(redis, new DiscoveryProperties());
    }

    @Test
    void touchWritesWhenThrottleAllows() {
        when(valueOperations.setIfAbsent(eq("discovery:online:touch:usr_a"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        store.touch("usr_a");

        verify(zSetOperations).add(eq(ONLINE_KEY), eq("usr_a"), anyDouble());
        verify(zSetOperations).removeRangeByScore(eq(ONLINE_KEY), anyDouble(), anyDouble());
    }

    @Test
    void touchSkipsWhenThrottled() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        store.touch("usr_a");

        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void blankUserIsIgnored() {
        store.touch("  ");

        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void countReadsWindow() {
        when(zSetOperations.count(eq(ONLINE_KEY), anyDouble(), anyDouble())).thenReturn(12L);

        assertThat(store.count()).isEqualTo(12L);
    }

    @Test
    void countFallsBackToZeroOnFailure() {
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(store.count()).isZero();
    }

    @Test
    void countFallsBackToZeroWhenNull() {
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(null);

        assertThat(store.count()).isZero();
    }
}
