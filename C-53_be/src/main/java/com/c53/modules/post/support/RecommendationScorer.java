package com.c53.modules.post.support;

import com.c53.modules.auth.service.UserSummaryService.UserScoringProfile;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.topic.dto.TopicDtos.TopicSummary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息流推荐打分。
 *
 * <p>本期不依赖互动数据，得分由时间衰减、话题热度、作者资料完整度组成：
 * {@code 0.60 * timeScore + 0.25 * topicScore + 0.15 * authorScore}。
 * 互动模块上线后按 PRD 6.1.3 调整权重与交互项。</p>
 */
@Component
public class RecommendationScorer {

    private static final double TIME_WEIGHT = 0.60;
    private static final double TOPIC_WEIGHT = 0.25;
    private static final double AUTHOR_WEIGHT = 0.15;

    /** 时间衰减半衰期（小时）对应的时间常数。 */
    private static final double TIME_CONSTANT_HOURS = 24.0;

    /**
     * 一次打分所需的上下文。
     *
     * @param topics        话题 ID 到话题摘要的映射
     * @param maxTopicCount 候选池内最大话题动态数
     * @param profiles      作者 ID 到资料摘要的映射
     */
    public record ScoreContext(Map<String, TopicSummary> topics,
                               long maxTopicCount,
                               Map<String, UserScoringProfile> profiles) {
    }

    /**
     * 带分数的动态。
     *
     * @param post  动态
     * @param score 得分
     */
    public record Scored(PostEntity post, double score) {
    }

    /**
     * 计算单条动态的得分。
     *
     * @param post    动态
     * @param context 打分上下文
     * @param now     当前时间
     * @return 0～1 之间的得分
     */
    public double score(PostEntity post, ScoreContext context, Instant now) {
        double timeScore = timeScore(post.getPublishedAt(), now);
        double topicScore = topicScore(post.getTopicId(), context);
        double authorScore = authorScore(post.getAuthorId(), context);
        return TIME_WEIGHT * timeScore + TOPIC_WEIGHT * topicScore + AUTHOR_WEIGHT * authorScore;
    }

    /**
     * 打分并排序，同时限制同一作者的连续出现次数。
     *
     * @param candidates     候选动态
     * @param context        打分上下文
     * @param now            当前时间
     * @param maxAuthorStreak 同一作者连续出现上限
     * @return 排序后的动态列表
     */
    public List<PostEntity> rank(List<PostEntity> candidates, ScoreContext context,
                                 Instant now, int maxAuthorStreak) {
        List<Scored> scored = candidates.stream()
                .map(post -> new Scored(post, score(post, context, now)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(item -> item.post().getPublishedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.post().getId(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return applyAuthorStreak(scored, maxAuthorStreak);
    }

    private double timeScore(Instant publishedAt, Instant now) {
        if (publishedAt == null) {
            return 0;
        }
        double ageHours = Math.max(0, Duration.between(publishedAt, now).toMinutes() / 60.0);
        return Math.min(1.0, Math.exp(-ageHours / TIME_CONSTANT_HOURS));
    }

    private double topicScore(String topicId, ScoreContext context) {
        if (topicId == null || context.maxTopicCount() <= 0) {
            return 0;
        }
        TopicSummary topic = context.topics().get(topicId);
        if (topic == null || topic.postCount() <= 0) {
            return 0;
        }
        return Math.min(1.0, topic.postCount() / (double) context.maxTopicCount());
    }

    private double authorScore(String authorId, ScoreContext context) {
        UserScoringProfile profile = context.profiles().get(authorId);
        if (profile == null) {
            return 0;
        }
        double score = 0;
        if (notBlank(profile.avatarUrl())) {
            score += 0.4;
        }
        if (notBlank(profile.bio())) {
            score += 0.3;
        }
        if (notBlank(profile.profession())) {
            score += 0.3;
        }
        return score;
    }

    /**
     * 先按作者密度筛一遍，再把被推迟的内容追加到末尾。
     *
     * <p>追加部分只会在前置结果不足一页时被展示，属于“放宽密度”的兜底。</p>
     */
    private List<PostEntity> applyAuthorStreak(List<Scored> scored, int maxAuthorStreak) {
        int limit = Math.max(1, maxAuthorStreak);
        List<PostEntity> result = new ArrayList<>(scored.size());
        List<PostEntity> deferred = new ArrayList<>();
        Map<String, Integer> streak = new HashMap<>();
        for (Scored item : scored) {
            PostEntity post = item.post();
            int count = streak.getOrDefault(post.getAuthorId(), 0);
            if (count >= limit) {
                deferred.add(post);
                continue;
            }
            result.add(post);
            streak.merge(post.getAuthorId(), 1, Integer::sum);
        }
        result.addAll(deferred);
        return result;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
