package com.c53.modules.media.support;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectKeysTest {

    @Test
    void buildsFinalKeyWithEnvAndDate() {
        String key = ObjectKeys.finalKey("dev", "IMAGE",
                Instant.parse("2026-10-23T10:00:00Z"), "usr_1", "med_2", "image/jpeg");
        assertThat(key).isEqualTo("dev/image/2026/10/23/usr_1/med_2.jpg");
    }

    @Test
    void buildsTempKey() {
        assertThat(ObjectKeys.tempKey("prod", "usr_1", "upl_2", "video/mp4"))
                .isEqualTo("prod/temp/usr_1/upl_2/source.mp4");
    }

    @Test
    void stripsUnsafeCharacters() {
        assertThat(ObjectKeys.tempKey("../../etc", "usr/../1", "upl 2", "video/mp4"))
                .isEqualTo("etc/temp/usr1/upl2/source.mp4");
    }

    @Test
    void extensionFallsBackToBin() {
        assertThat(ObjectKeys.extension(null)).isEqualTo("bin");
        assertThat(ObjectKeys.extension("image/webp")).isEqualTo("webp");
    }
}
