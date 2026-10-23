package com.c53.modules.post.support;

import com.c53.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void roundTripsTimeAndId() {
        Instant time = Instant.parse("2026-10-23T08:00:00.123Z");
        String encoded = CursorCodec.encode(time, "pst_01K5ABC");
        CursorCodec.Cursor decoded = CursorCodec.decode(encoded);
        assertThat(decoded.time()).isEqualTo(time);
        assertThat(decoded.id()).isEqualTo("pst_01K5ABC");
    }

    @Test
    void emptyCursorMeansFirstPage() {
        assertThat(CursorCodec.decode(null)).isNull();
        assertThat(CursorCodec.decode("  ")).isNull();
    }

    @Test
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> CursorCodec.decode("not-base64!"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");
    }
}
