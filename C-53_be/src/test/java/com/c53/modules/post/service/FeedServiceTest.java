package com.c53.modules.post.service.impl;

import com.c53.common.ApiException;
import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.config.DiscoveryProperties;
import com.c53.config.PostProperties;
import com.c53.modules.auth.service.AuthSessionGuard;
import com.c53.modules.auth.service.UserSummaryService;
import com.c53.modules.interaction.service.InteractionQueryService;
import com.c53.modules.media.service.MediaService;
import com.c53.modules.post.dto.PostDtos.PostListResponse;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.repository.PostRepository;
import com.c53.modules.post.support.RecommendationScorer;
import com.c53.modules.social.service.FollowQueryService;
import com.c53.modules.topic.service.TopicService;
import com.c53.security.AuthPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 信息流分支的单元测试：登录约束、游标分页、推荐流过滤自己。
 */
class FeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T08:00:00Z");
    private static final AuthPrincipal ALICE = new AuthPrincipal("usr_alice", "ses_1");

    private PostRepository postRepository;
    private UserSummaryService userSummaryService;
    private FollowQueryService followQueryService;
    private RedisRateLimiter rateLimiter;
    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        MediaService mediaService = mock(MediaService.class);
        TopicService topicService = mock(TopicService.class);
        userSummaryService = mock(UserSummaryService.class);
        AuthSessionGuard guard = mock(AuthSessionGuard.class);
        rateLimiter = mock(RedisRateLimiter.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        followQueryService = mock(FollowQueryService.class);
        DiscoveryProperties discoveryProperties = new DiscoveryProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        when(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(RateLimitResult.allowed(10));
        when(userSummaryService.findActiveUserIds(any()))
                .thenAnswer(invocation -> Set.copyOf(invocation.<Collection<String>>getArgument(0)));

        InteractionQueryService interactionQueryService = mock(InteractionQueryService.class);
        when(interactionQueryService.findSummaries(any(), any())).thenReturn(java.util.Map.of());
        service = new PostServiceImpl(postRepository, mediaService, topicService, userSummaryService,
                guard, rateLimiter, new PostProperties(), transactionTemplate, clock,
                followQueryService, discoveryProperties, new RecommendationScorer(),
                new SimpleMeterRegistry(), interactionQueryService);
    }

    @Test
    void followingRequiresLogin() {
        assertThatThrownBy(() -> service.feed(null, "FOLLOWING", null, 20, "1.1.1.1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void latestUsesCursorPagination() {
        List<PostEntity> rows = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            rows.add(post("pst_" + i, "usr_bob", NOW.minusSeconds(60L * i)));
        }
        when(postRepository.findLatest(any(), any(), anyInt())).thenReturn(rows);

        PostListResponse response = service.feed(ALICE, "LATEST", null, 20, "1.1.1.1");

        assertThat(response.items()).hasSize(20);
        assertThat(response.nextCursor()).isNotBlank();
        verify(rateLimiter).tryAcquire(anyString(), anyInt(), any(Duration.class));
    }

    @Test
    void followingExcludesViewer() {
        when(followQueryService.findFollowingIds("usr_alice")).thenReturn(Set.of("usr_bob"));
        when(postRepository.findByAuthors(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(post("pst_1", "usr_bob", NOW)));

        PostListResponse response = service.feed(ALICE, "FOLLOWING", null, 20, "1.1.1.1");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).author()).isNull();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void followingReturnsEmptyWhenNoFollows() {
        when(followQueryService.findFollowingIds("usr_alice")).thenReturn(Set.of());

        PostListResponse response = service.feed(ALICE, "FOLLOWING", null, 20, "1.1.1.1");

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void recommendedExcludesOwnPostsAndHasNoCursor() {
        when(postRepository.findRecommendedCandidates(any(), anyInt())).thenReturn(List.of(
                post("pst_mine", "usr_alice", NOW),
                post("pst_other", "usr_bob", NOW.minusSeconds(3600))));

        PostListResponse response = service.feed(ALICE, "RECOMMENDED", null, 20, "1.1.1.1");

        assertThat(response.items()).extracting(item -> item.id())
                .containsExactly("pst_other");
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void anonymousUsesIpRateLimitKey() {
        when(postRepository.findLatest(any(), any(), anyInt())).thenReturn(List.of());

        service.feed(null, "LATEST", null, 20, "9.9.9.9");

        verify(rateLimiter).tryAcquire(
                org.mockito.ArgumentMatchers.eq("discovery:rate:feed:ip:9.9.9.9"),
                anyInt(), any(Duration.class));
    }

    private static PostEntity post(String id, String authorId, Instant publishedAt) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setStatus("PUBLISHED");
        post.setContent("内容");
        post.setPublishedAt(publishedAt);
        post.setVersion(1);
        return post;
    }
}
