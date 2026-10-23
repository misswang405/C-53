package com.c53.modules.post.support;

import com.c53.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentRulesTest {

    @Test
    void normalizesLineEndingsAndBlankLines() {
        assertThat(ContentRules.normalize("  第一段\r\n\r\n\r\n第二段  "))
                .isEqualTo("第一段\n\n第二段");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> ContentRules.normalize("abc\u0001def"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void countsEmojiAsOneCodePoint() {
        assertThat(ContentRules.length("Hello😀")).isEqualTo(6);
    }

    @Test
    void publishRequiresContent() {
        assertThatThrownBy(() -> ContentRules.requirePublish(""))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("CONTENT_EMPTY");
    }

    @Test
    void draftAllowsEmptyButNotOverlong() {
        ContentRules.requireDraft("");
        assertThatThrownBy(() -> ContentRules.requireDraft("a".repeat(601)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("CONTENT_TOO_LONG");
    }

    @Test
    void summaryKeepsFirst120CodePoints() {
        String summary = ContentRules.summary("a".repeat(200));
        assertThat(summary).hasSize(121).endsWith("…");
    }
}
