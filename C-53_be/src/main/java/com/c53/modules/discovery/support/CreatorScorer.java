package com.c53.modules.discovery.support;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 推荐创作者打分。
 *
 * <p>得分由活跃度、粉丝数、资料完整度与随机因子组成：
 * {@code 0.40 * activity + 0.30 * follower + 0.20 * profile + 0.10 * random}。
 * 统计因子在候选池内归一化，最大值为 0 时该因子记 0。</p>
 */
@Component
public class CreatorScorer {

    private static final double ACTIVITY_WEIGHT = 0.40;
    private static final double FOLLOWER_WEIGHT = 0.30;
    private static final double PROFILE_WEIGHT = 0.20;
    private static final double RANDOM_WEIGHT = 0.10;

    /**
     * 打分候选。
     *
     * @param id              用户 ID
     * @param nickname        昵称
     * @param username        用户名
     * @param initials        头像占位字符
     * @param avatarUrl       头像地址
     * @param profession      职业
     * @param bio             个人简介
     * @param recentPostCount 近 30 天公开动态数
     * @param followerCount   粉丝数
     */
    public record Candidate(String id, String nickname, String username, String initials,
                            String avatarUrl, String profession, String bio,
                            long recentPostCount, long followerCount) {
    }

    /**
     * 带分数的候选。
     *
     * @param candidate 候选
     * @param score     得分
     */
    public record Scored(Candidate candidate, double score) {
    }

    /**
     * 打分并排序。
     *
     * @param candidates 候选用户
     * @return 按得分降序排列的结果
     */
    public List<Scored> rank(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        long maxPosts = candidates.stream().mapToLong(Candidate::recentPostCount).max().orElse(0);
        long maxFollowers = candidates.stream().mapToLong(Candidate::followerCount).max().orElse(0);
        return candidates.stream()
                .map(candidate -> new Scored(candidate,
                        score(candidate, maxPosts, maxFollowers, ThreadLocalRandom.current().nextDouble())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(item -> item.candidate().id()))
                .toList();
    }

    /**
     * 计算单个候选的得分。
     *
     * @param candidate    候选
     * @param maxPosts     候选池内最大动态数
     * @param maxFollowers 候选池内最大粉丝数
     * @param random       随机因子，取值 [0, 1)
     * @return 得分
     */
    public double score(Candidate candidate, long maxPosts, long maxFollowers, double random) {
        return ACTIVITY_WEIGHT * normalize(candidate.recentPostCount(), maxPosts)
                + FOLLOWER_WEIGHT * normalize(candidate.followerCount(), maxFollowers)
                + PROFILE_WEIGHT * profileScore(candidate)
                + RANDOM_WEIGHT * random;
    }

    private double normalize(long value, long max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return Math.min(1.0, value / (double) max);
    }

    private double profileScore(Candidate candidate) {
        double score = 0;
        if (notBlank(candidate.avatarUrl())) {
            score += 0.4;
        }
        if (notBlank(candidate.bio())) {
            score += 0.3;
        }
        if (notBlank(candidate.profession())) {
            score += 0.3;
        }
        return score;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
