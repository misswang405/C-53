package com.c53.modules.post.support;

import com.c53.modules.auth.service.UserSummaryService.UserScoringProfile;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.topic.dto.TopicDtos.TopicSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScorerTest {

    private static final Instant NOW = Instant.parse("2026-10-23T08:00:00Z");

    private final RecommendationScorer scorer = new RecommendationScorer();

    @Test
    void newerPostScoresHigher() {
        PostEntity fresh = post("pst_1", "usr_a", "tpc_1", NOW.minusSeconds(600));
        PostEntity old = post("pst_2", "usr_a", "tpc_1", NOW.minusSeconds(86_400 * 3));
        RecommendationScorer.ScoreContext context = context(
                Map.of("tpc_1", new TopicSummary("tpc_1", "技术", 10)), 10,
                Map.of("usr_a", new UserScoringProfile("usr_a", "a.png", "工程师", "简介")));

        assertThat(scorer.score(fresh, context, NOW)).isGreaterThan(scorer.score(old, context, NOW));
    }

    @Test
    void limitsAuthorStreakAndAppendsDeferred() {
        List<PostEntity> candidates = List.of(
                post("pst_1", "usr_a", "tpc_1", NOW.minusSeconds(60)),
                post("pst_2", "usr_a", "tpc_1", NOW.minusSeconds(120)),
                post("pst_3", "usr_a", "tpc_1", NOW.minusSeconds(180)),
                post("pst_4", "usr_b", "tpc_1", NOW.minusSeconds(240)));
        RecommendationScorer.ScoreContext context = context(
                Map.of("tpc_1", new TopicSummary("tpc_1", "技术", 10)), 10,
                Map.of("usr_a", new UserScoringProfile("usr_a", "a.png", "工程师", "简介"),
                        "usr_b", new UserScoringProfile("usr_b", null, null, null)));

        List<PostEntity> ordered = scorer.rank(candidates, context, NOW, 2);

        assertThat(ordered).extracting(PostEntity::getId)
                .containsExactly("pst_1", "pst_2", "pst_4", "pst_3");
    }

    @Test
    void missingDataFallsBackToTimeOnly() {
        PostEntity item = post("pst_1", "usr_x", null, NOW);
        RecommendationScorer.ScoreContext context =
                new RecommendationScorer.ScoreContext(Map.of(), 0, Map.of());

        assertThat(scorer.score(item, context, NOW)).isBetween(0.59, 0.61);
    }

    private static RecommendationScorer.ScoreContext context(
            Map<String, TopicSummary> topics, long maxTopicCount,
            Map<String, UserScoringProfile> profiles) {
        return new RecommendationScorer.ScoreContext(topics, maxTopicCount, profiles);
    }

    private static PostEntity post(String id, String authorId, String topicId, Instant publishedAt) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setTopicId(topicId);
        post.setStatus("PUBLISHED");
        post.setContent("内容");
        post.setPublishedAt(publishedAt);
        post.setVersion(1);
        return post;
    }
}
