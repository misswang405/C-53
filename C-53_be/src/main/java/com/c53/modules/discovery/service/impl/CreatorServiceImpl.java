package com.c53.modules.discovery.service.impl;

import com.c53.common.ApiException;
import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.config.DiscoveryProperties;
import com.c53.modules.auth.service.UserSummaryService;
import com.c53.modules.auth.service.UserSummaryService.CreatorProfile;
import com.c53.modules.discovery.dto.CreatorDtos.CreatorListResponse;
import com.c53.modules.discovery.dto.CreatorDtos.CreatorSummary;
import com.c53.modules.discovery.redis.CreatorPoolCache;
import com.c53.modules.discovery.service.CreatorService;
import com.c53.modules.discovery.support.CreatorScorer;
import com.c53.modules.discovery.support.CreatorScorer.Candidate;
import com.c53.modules.discovery.support.CreatorScorer.Scored;
import com.c53.modules.post.service.PostService;
import com.c53.modules.social.service.FollowQueryService;
import com.c53.security.AuthPrincipal;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐创作者实现。
 *
 * <p>候选池按得分缓存 5 分钟；每次请求在应用层排除自己与已关注用户，
 * 再取前 N 条。关注模块上线前，关注集合为空、关系恒为 NONE。</p>
 */
@Service
public class CreatorServiceImpl implements CreatorService {

    /** 用户摘要服务，用于查询候选创作者的基础资料。 */
    private final UserSummaryService userSummaryService;
    /** 帖子服务，用于统计创作者近 30 天的发布数量。 */
    private final PostService postService;
    /** 关注查询服务，用于获取当前用户的关注集合及关注关系。 */
    private final FollowQueryService followQueryService;
    /** 创作者打分器，负责对候选池按得分排序。 */
    private final CreatorScorer scorer;
    /** 候选池缓存，避免每次请求都重建并打分。 */
    private final CreatorPoolCache cache;
    /** 基于 Redis 的限流器，按 IP 限制请求频率。 */
    private final RedisRateLimiter rateLimiter;
    /** 发现模块配置项（候选上限、结果条数、限流阈值等）。 */
    private final DiscoveryProperties properties;
    /** 时钟，用于计算活跃窗口的起始时间，便于测试替换。 */
    private final Clock clock;

    public CreatorServiceImpl(UserSummaryService userSummaryService,
                              PostService postService,
                              FollowQueryService followQueryService,
                              CreatorScorer scorer,
                              CreatorPoolCache cache,
                              RedisRateLimiter rateLimiter,
                              DiscoveryProperties properties,
                              Clock clock) {
        this.userSummaryService = userSummaryService;
        this.postService = postService;
        this.followQueryService = followQueryService;
        this.scorer = scorer;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 返回推荐创作者列表。
     *
     * <p>流程：先按 IP 限流；再从缓存取候选池（未命中则重建并回填）；
     * 接着在应用层排除“自己”和“已关注”的用户；最后截取前 N 条返回。</p>
     *
     * @param principal 当前登录用户，未登录时可为 null
     * @param clientIp  客户端 IP，用于限流
     * @return 推荐创作者列表响应
     */
    @Override
    public CreatorListResponse recommended(AuthPrincipal principal, String clientIp) {
        enforceRateLimit(clientIp);

        // 优先读缓存，未命中则重建候选池并写回缓存
        List<Scored> pool = cache.get().orElseGet(() -> {
            List<Scored> rebuilt = rebuildPool();
            cache.put(rebuilt);
            return rebuilt;
        });
        if (pool.isEmpty()) {
            return new CreatorListResponse(List.of());
        }

        // 未登录视为无关注关系，关注集合为空
        Set<String> following = principal == null
                ? Set.of()
                : followQueryService.findFollowingIds(principal.userId());

        List<CreatorSummary> items = pool.stream()
                // 排除自己（登录用户不应看到自己的推荐位）
                .filter(item -> principal == null
                        || !item.candidate().id().equals(principal.userId()))
                // 排除已关注的创作者
                .filter(item -> !following.contains(item.candidate().id()))
                // 截取配置的最大返回条数
                .limit(properties.getCreator().getResultLimit())
                .map(item -> toSummary(item.candidate(), principal))
                .toList();
        return new CreatorListResponse(items);
    }

    /**
     * 重建候选池：查询活跃用户、统计近 30 天发布数、打分排序。
     * 粉丝数在关注模块上线前固定为 0。
     */
    private List<Scored> rebuildPool() {
        // 取出候选范围内的活跃用户
        List<CreatorProfile> profiles =
                userSummaryService.findActiveCandidates(properties.getCreator().getCandidateLimit());
        if (profiles.isEmpty()) {
            return List.of();
        }
        // 统计这些用户在活跃窗口内的发布数量
        Set<String> ids = profiles.stream().map(CreatorProfile::id).collect(Collectors.toSet());
        Instant since = clock.instant().minus(properties.getCreator().getActiveWindow());
        Map<String, Long> postCounts = postService.countPublishedSince(ids, since);

        // 组装打分候选对象；粉丝数暂固定为 0（关注模块未上线）
        List<Candidate> candidates = profiles.stream()
                .map(profile -> new Candidate(
                        profile.id(), profile.nickname(), profile.username(), profile.initials(),
                        profile.avatarUrl(), profile.profession(), profile.bio(),
                        postCounts.getOrDefault(profile.id(), 0L), 0L))
                .toList();
        return scorer.rank(candidates);
    }

    /**
     * 将候选对象转换为对外返回的创作者摘要。
     *
     * @param candidate 候选创作者
     * @param principal 当前登录用户，未登录时关系恒为 NONE
     * @return 创作者摘要
     */
    private CreatorSummary toSummary(Candidate candidate, AuthPrincipal principal) {
        // 未登录时无关注关系，否则查询与目标创作者的真实关系
        String relationship = principal == null
                ? FollowQueryService.Relationship.NONE.name()
                : followQueryService.relationship(principal.userId(), candidate.id()).name();
        return new CreatorSummary(
                candidate.id(), candidate.nickname(), candidate.username(),
                candidate.avatarUrl(), candidate.initials(), candidate.profession(), relationship);
    }

    /**
     * 按客户端 IP 执行限流，超出阈值时抛出 429 异常。
     *
     * @param clientIp 客户端 IP
     */
    private void enforceRateLimit(String clientIp) {
        String key = "discovery:rate:creators:ip:" + safeIp(clientIp);
        RateLimitResult result = rateLimiter.tryAcquire(
                key, properties.getCreator().getPerMinutePerIp(), Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "操作过于频繁，请稍后再试",
                    Map.of("retryAfter", result.retryAfterSeconds()));
        }
    }

    /** IP 为空时归一化为 "unknown"，避免限流 key 出现 null。 */
    private String safeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }
}
