package com.c53.modules.post.support;

import com.c53.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedScopeTest {

    @Test
    void defaultsToRecommended() {
        assertThat(FeedScope.parse(null)).isEqualTo(FeedScope.RECOMMENDED);
        assertThat(FeedScope.parse("  ")).isEqualTo(FeedScope.RECOMMENDED);
    }

    @Test
    void parsesCaseInsensitively() {
        assertThat(FeedScope.parse("latest")).isEqualTo(FeedScope.LATEST);
        assertThat(FeedScope.parse(" Following ")).isEqualTo(FeedScope.FOLLOWING);
        assertThat(FeedScope.parse("RECOMMENDED")).isEqualTo(FeedScope.RECOMMENDED);
    }

    @Test
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> FeedScope.parse("HOT"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");
    }
}
