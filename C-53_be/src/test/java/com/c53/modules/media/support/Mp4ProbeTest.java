package com.c53.modules.media.support;

import com.c53.common.ApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mp4ProbeTest {

    @Test
    void readsDurationAndResolutionFromMoov() {
        byte[] mp4 = syntheticMp4("avc1", "vide", 300, 5000);
        Mp4Probe.ProbeResult result = Mp4Probe.probe(mp4);
        assertThat(result.durationMs()).isEqualTo(5000);
        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.height()).isEqualTo(1080);
        assertThat(result.fps()).isCloseTo(60.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void rejectsNonMp4() {
        assertThatThrownBy(() -> Mp4Probe.probe(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("MEDIA_INVALID");
    }

    @Test
    void rejectsUnsupportedVideoCodec() {
        byte[] mp4 = syntheticMp4("hvc1", "vide", 300, 5000);
        assertThatThrownBy(() -> Mp4Probe.probe(mp4))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("H.264");
    }

    private static byte[] syntheticMp4(String videoCodec, String handler, int sampleCount, int durationMs) {
        byte[] ftyp = box("ftyp", concat(
                "isom".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 0, 0, 0},
                "isom".getBytes(StandardCharsets.US_ASCII)));

        byte[] mvhd = box("mvhd", concat(
                intBytes(0),
                intBytes(0),
                intBytes(0),
                intBytes(1000),
                intBytes(durationMs)));

        byte[] tkhdPayload = new byte[84];
        ByteBuffer tkhd = ByteBuffer.wrap(tkhdPayload);
        tkhd.putInt(0, 0);
        tkhd.putInt(76, 1920 << 16);
        tkhd.putInt(80, 1080 << 16);
        byte[] tkhdBox = box("tkhd", tkhdPayload);

        byte[] mdhd = box("mdhd", concat(
                intBytes(0), intBytes(0), intBytes(0),
                intBytes(1000), intBytes(durationMs)));

        byte[] hdlr = box("hdlr", concat(
                intBytes(0),
                intBytes(0),
                handler.getBytes(StandardCharsets.US_ASCII)));

        byte[] stsd = box("stsd", concat(
                intBytes(0),
                intBytes(1),
                intBytes(16),
                videoCodec.getBytes(StandardCharsets.US_ASCII)));

        byte[] stsz = box("stsz", concat(
                intBytes(0),
                intBytes(0),
                intBytes(sampleCount)));

        byte[] stbl = box("stbl", concat(stsd, stsz));
        byte[] minf = box("minf", stbl);
        byte[] mdia = box("mdia", concat(mdhd, hdlr, minf));
        byte[] trak = box("trak", concat(tkhdBox, mdia));
        byte[] moov = box("moov", concat(mvhd, trak));
        return concat(ftyp, moov);
    }

    private static byte[] box(String type, byte[] payload) {
        return concat(intBytes(8 + payload.length),
                type.getBytes(StandardCharsets.US_ASCII),
                payload);
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.writeBytes(array);
        }
        return output.toByteArray();
    }
}
