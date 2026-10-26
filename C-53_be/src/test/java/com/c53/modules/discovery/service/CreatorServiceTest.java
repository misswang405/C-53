package com.c53.modules.discovery.service;

import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.config.DiscoveryProperties;
import com.c53.modules.auth.service.UserSummaryService;
import com.c53.modules.auth.service.UserSummaryService.CreatorProfile;
import com.c53.modules.discovery.dto.CreatorDtos.CreatorListResponse;
import com.c53.modules.discovery.redis.CreatorPoolCache;
import com.c53.modules.discovery.service.impl.CreatorServiceImpl;
import com.c53.modules.discovery.support.CreatorScorer;
import com.c53.modules.discovery.support.CreatorScorer.Candidate;
import com.c53.modules.discovery.support.CreatorScorer.Scored;
import com.c53.modules.post.service.PostService;
import com.c53.modules.social.service.FollowQueryService;
import com.c53.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐创作者的过滤与缓存行为。
 */
class CreatorServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T08:00:00Z");
    private static final AuthPrincipal ALICE = new AuthPrincipal("usr_alice", "ses_1");

    private UserSummaryService userSummaryService;
    private PostService postService;
    private FollowQueryService followQueryService;
    private CreatorPoolCache cache;
    private CreatorServiceImpl service;

    @BeforeEach
    void setUp() {
        userSummaryService = mock(UserSummaryService.class);
        postService = mock(PostService.class);
        followQueryService = mock(FollowQueryService.class);
        cache = mock(CreatorPoolCache.class);
        RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(RateLimitResult.allowed(10));

        service = new CreatorServiceImpl(userSummaryService, postService, followQueryService,
                new CreatorScorer(), cache, rateLimiter, new DiscoveryProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void excludesSelfAndFollowedUsers() {
        when(cache.get()).thenReturn(Optional.of(List.of(
                scored("usr_alice", 1.0),
                scored("usr_bob", 0.9),
                scored("usr_carol", 0.8))));
        when(followQueryService.findFollowingIds("usr_alice")).thenReturn(Set.of("usr_bob"));
        when(followQueryService.relationship("usr_alice", "usr_carol"))
                .thenReturn(FollowQueryService.Relationship.NONE);

        CreatorListResponse response = service.recommended(ALICE, "1.1.1.1");

        assertThat(response.items()).extracting(item -> item.id())
                .containsExactly("usr_carol");
        assertThat(response.items().get(0).relationship()).isEqualTo("NONE");
    }

    @Test
    void rebuildsPoolWhenCacheMisses() {
        when(cache.get()).thenReturn(Optional.empty());
        when(userSummaryService.findActiveCandidates(anyInt())).thenReturn(List.of(
                new CreatorProfile("usr_bob", "小 B", "bob", "B", null, "工程师", "简介")));
        when(postService.countPublishedSince(any(), any())).thenReturn(Map.of("usr_bob", 3L));

        CreatorListResponse response = service.recommended(null, "1.1.1.1");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).relationship()).isEqualTo("NONE");
        verify(cache).put(any());
    }

    @Test
    void limitsResultSize() {
        List<Scored> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pool.add(scored("usr_" + i, 1.0 - i * 0.01));
        }
        when(cache.get()).thenReturn(Optional.of(pool));

        CreatorListResponse response = service.recommended(null, "1.1.1.1");

        assertThat(response.items()).hasSize(6);
    }

    private static Scored scored(String id, double score) {
        Candidate candidate = new Candidate(id, id, id, id.substring(4, 5).toUpperCase(),
                null, null, null, 0, 0);
        return new Scored(candidate, score);
    }
}
