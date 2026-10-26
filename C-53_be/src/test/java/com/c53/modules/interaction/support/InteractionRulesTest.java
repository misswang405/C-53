package com.c53.modules.interaction.support;

import com.c53.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteractionRulesTest {

    @Test
    void normalizesWhitespaceAndLineBreaks() {
        assertThat(InteractionRules.normalizeComment(" \r\n你好\r ")).isEqualTo("你好");
    }

    @Test
    void countsEmojiAsOneCodePoint() {
        String content = "😀".repeat(500);
        assertThat(InteractionRules.normalizeComment(content)).hasSize(1000);
    }

    @Test
    void rejectsEmptyAndTooLongComments() {
        assertThatThrownBy(() -> InteractionRules.normalizeComment(" \n "))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CONTENT_EMPTY");
        assertThatThrownBy(() -> InteractionRules.normalizeComment("a".repeat(501)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CONTENT_TOO_LONG");
    }

    @Test
    void acceptsSupportedShareChannels() {
        assertThat(InteractionRules.normalizeChannel(" wechat ")).isEqualTo("WECHAT");
        assertThat(InteractionRules.normalizeChannel("QQ")).isEqualTo("QQ");
    }

    @Test
    void rejectsUnsupportedShareChannels() {
        assertThatThrownBy(() -> InteractionRules.normalizeChannel("WEIBO"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_SHARE_CHANNEL");
    }
}
