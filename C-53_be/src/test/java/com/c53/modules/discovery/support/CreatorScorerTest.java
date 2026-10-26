package com.c53.modules.discovery.support;

import com.c53.modules.discovery.support.CreatorScorer.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreatorScorerTest {

    private final CreatorScorer scorer = new CreatorScorer();

    @Test
    void activeUserWithCompleteProfileScoresHigher() {
        Candidate active = new Candidate("usr_a", "小 A", "a", "A", "a.png", "工程师", "简介", 10, 0);
        Candidate idle = new Candidate("usr_b", "小 B", "b", "B", null, null, null, 0, 0);

        assertThat(scorer.score(active, 10, 0, 0.5))
                .isGreaterThan(scorer.score(idle, 10, 0, 0.5));
    }

    @Test
    void normalizesByMaxValue() {
        Candidate top = new Candidate("usr_a", "小 A", "a", "A", null, null, null, 10, 5);
        Candidate low = new Candidate("usr_b", "小 B", "b", "B", null, null, null, 5, 0);

        assertThat(scorer.score(top, 10, 5, 0))
                .isGreaterThan(scorer.score(low, 10, 5, 0));
    }

    @Test
    void rankReturnsAllCandidates() {
        List<Candidate> candidates = List.of(
                new Candidate("usr_a", "小 A", "a", "A", null, null, null, 1, 0),
                new Candidate("usr_b", "小 B", "b", "B", null, null, null, 0, 0));

        List<CreatorScorer.Scored> ranked = scorer.rank(candidates);

        assertThat(ranked).hasSize(2);
        assertThat(ranked).extracting(item -> item.candidate().id())
                .containsExactlyInAnyOrder("usr_a", "usr_b");
    }
}
