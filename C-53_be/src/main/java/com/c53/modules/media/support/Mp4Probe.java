package com.c53.modules.media.support;

import com.c53.common.ApiException;
import com.c53.modules.media.storage.ObjectStorage;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MP4 容器探测。
 *
 * <p>只解析 {@code moov} 中的元数据，不下载整个视频。直传场景通过 OSS Range
 * 读取文件头定位 {@code moov}，再读取该 Box，读取量上限 8MB。</p>
 *
 * <p>校验项：MP4 容器、H.264 视频轨（{@code avc1}/{@code avc3}）、
 * AAC 音频轨（{@code mp4a}）或无音轨；时长、分辨率、平均帧率由调用方按
 * 配置再校验。</p>
 */
public final class Mp4Probe {

    /** moov 允许的最大字节数。 */
    public static final long MAX_MOOV_BYTES = 8L * 1024 * 1024;

    private Mp4Probe() {
    }

    /**
     * 范围读取器，闭区间。
     */
    @FunctionalInterface
    public interface RangeReader {
        /**
         * 读取指定范围。
         *
         * @param start        起始字节，包含
         * @param endInclusive 结束字节，包含
         * @return 字节内容
         */
        byte[] read(long start, long endInclusive);
    }

    /**
     * 探测结果。
     *
     * @param durationMs 时长毫秒
     * @param width      宽
     * @param height     高
     * @param fps        平均帧率，无法计算时为 0
     */
    public record ProbeResult(long durationMs, int width, int height, double fps) {
    }

    /**
     * 从内存字节探测，用于服务端上传。
     *
     * @param data 完整文件内容
     * @return 探测结果
     */
    public static ProbeResult probe(byte[] data) {
        return probe(data.length, (start, end) -> {
            int from = (int) Math.max(0, start);
            int to = (int) Math.min(data.length - 1, end);
            if (to < from) {
                return new byte[0];
            }
            return Arrays.copyOfRange(data, from, to + 1);
        });
    }

    /**
     * 从对象存储探测，用于直传确认。
     *
     * @param storage 对象存储
     * @param key     临时对象 Key
     * @param size    对象字节数
     * @return 探测结果
     */
    public static ProbeResult probe(ObjectStorage storage, String key, long size) {
        return probe(size, (start, end) -> storage.readRange(key, start, end));
    }

    /**
     * 核心探测逻辑。
     *
     * @param totalSize 文件总字节数
     * @param reader    范围读取器
     * @return 探测结果
     */
    public static ProbeResult probe(long totalSize, RangeReader reader) {
        if (totalSize < 16) {
            throw invalid("视频文件不完整");
        }
        byte[] head = reader.read(0, 15);
        if (head.length < 8 || !"ftyp".equals(ascii(head, 4, 4))) {
            throw invalid("不是有效的 MP4 文件");
        }

        long offset = 0;
        long moovOffset = -1;
        long moovSize = 0;
        int moovHeaderSize = 8;
        while (offset + 8 <= totalSize) {
            byte[] header = reader.read(offset, Math.min(offset + 15, totalSize - 1));
            if (header.length < 8) {
                break;
            }
            long size = u32(header, 0);
            String type = ascii(header, 4, 4);
            int headerSize = 8;
            if (size == 1) {
                if (header.length < 16) {
                    throw invalid("MP4 Box 头不完整");
                }
                size = u64(header, 8);
                headerSize = 16;
            } else if (size == 0) {
                size = totalSize - offset;
            }
            if (size < headerSize || offset + size > totalSize) {
                throw invalid("MP4 Box 长度异常");
            }
            if ("moov".equals(type)) {
                moovOffset = offset;
                moovSize = size;
                moovHeaderSize = headerSize;
                break;
            }
            offset += size;
        }
        if (moovOffset < 0) {
            throw invalid("MP4 缺少 moov");
        }
        if (moovSize > MAX_MOOV_BYTES) {
            throw invalid("视频元数据过大");
        }

        byte[] moov = reader.read(moovOffset + moovHeaderSize, moovOffset + moovSize - 1);
        List<Box> moovChildren = boxes(moov, 0, moov.length);

        Box mvhd = find(moovChildren, "mvhd");
        if (mvhd == null) {
            throw invalid("MP4 缺少 mvhd");
        }
        long[] movieTime = parseMvhd(moov, mvhd);
        long movieTimescale = movieTime[0];
        long movieDuration = movieTime[1];

        Track video = null;
        boolean hasUnsupportedAudio = false;
        for (Box box : moovChildren) {
            if (!"trak".equals(box.type())) {
                continue;
            }
            Track track = parseTrack(moov, box);
            if (track == null) {
                continue;
            }
            if ("vide".equals(track.handler())) {
                video = track;
            } else if ("soun".equals(track.handler())) {
                if (track.codec() == null || !track.codec().startsWith("mp4a")) {
                    hasUnsupportedAudio = true;
                }
            }
        }
        if (video == null) {
            throw invalid("视频缺少画面轨道");
        }
        if (video.codec() == null || !(video.codec().startsWith("avc1") || video.codec().startsWith("avc3"))) {
            throw invalid("仅支持 H.264 视频");
        }
        if (hasUnsupportedAudio) {
            throw invalid("仅支持 AAC 音频");
        }

        long timescale = movieTimescale > 0 ? movieTimescale : video.timescale();
        long duration = movieDuration > 0 ? movieDuration : video.duration();
        if (timescale <= 0 || duration <= 0) {
            throw invalid("无法读取视频时长");
        }
        long durationMs = Math.round(duration * 1000.0 / timescale);
        int width = video.width();
        int height = video.height();
        if (width <= 0 || height <= 0) {
            throw invalid("无法读取视频分辨率");
        }
        double fps = video.samples() > 0 && durationMs > 0
                ? video.samples() * 1000.0 / durationMs
                : 0;

        return new ProbeResult(durationMs, width, height, fps);
    }

    /**
     * MP4 Box 的位置与类型，偏移量相对于所在字节数组。
     *
     * @param type         Box 类型
     * @param payloadStart 负载起始偏移
     * @param payloadEnd   负载结束偏移，不包含
     */
    private record Box(String type, int payloadStart, int payloadEnd) {
    }

    /**
     * 轨道元数据。
     */
    private record Track(String handler, String codec, int width, int height,
                         long samples, long timescale, long duration) {
    }

    /**
     * 解析一个 trak。
     */
    private static Track parseTrack(byte[] data, Box trak) {
        List<Box> children = boxes(data, trak.payloadStart(), trak.payloadEnd());
        Box tkhd = find(children, "tkhd");
        Box mdia = find(children, "mdia");
        if (mdia == null) {
            return null;
        }
        List<Box> mdiaChildren = boxes(data, mdia.payloadStart(), mdia.payloadEnd());
        Box hdlr = find(mdiaChildren, "hdlr");
        if (hdlr == null) {
            return null;
        }
        String handler = ascii(data, hdlr.payloadStart() + 8, 4);

        long timescale = 0;
        long duration = 0;
        Box mdhd = find(mdiaChildren, "mdhd");
        if (mdhd != null) {
            long[] mediaTime = parseMdhd(data, mdhd);
            timescale = mediaTime[0];
            duration = mediaTime[1];
        }

        String codec = null;
        long samples = 0;
        Box minf = find(mdiaChildren, "minf");
        if (minf != null) {
            Box stbl = find(boxes(data, minf.payloadStart(), minf.payloadEnd()), "stbl");
            if (stbl != null) {
                List<Box> stblChildren = boxes(data, stbl.payloadStart(), stbl.payloadEnd());
                Box stsd = find(stblChildren, "stsd");
                if (stsd != null) {
                    codec = parseStsdCodec(data, stsd);
                }
                Box stsz = find(stblChildren, "stsz");
                if (stsz != null) {
                    samples = parseSampleCount(data, stsz);
                }
            }
        }

        int width = 0;
        int height = 0;
        if (tkhd != null) {
            int[] size = parseTkhdSize(data, tkhd);
            width = size[0];
            height = size[1];
        }
        return new Track(handler, codec, width, height, samples, timescale, duration);
    }

    private static long[] parseMvhd(byte[] data, Box box) {
        int start = box.payloadStart();
        int version = data[start] & 0xFF;
        if (version == 1) {
            return new long[]{u32(data, start + 20), u64(data, start + 24)};
        }
        return new long[]{u32(data, start + 12), u32(data, start + 16)};
    }

    private static long[] parseMdhd(byte[] data, Box box) {
        int start = box.payloadStart();
        int version = data[start] & 0xFF;
        if (version == 1) {
            return new long[]{u32(data, start + 20), u64(data, start + 24)};
        }
        return new long[]{u32(data, start + 12), u32(data, start + 16)};
    }

    private static int[] parseTkhdSize(byte[] data, Box box) {
        int start = box.payloadStart();
        int version = data[start] & 0xFF;
        int offset = version == 1 ? start + 88 : start + 76;
        return new int[]{(int) (u32(data, offset) >>> 16), (int) (u32(data, offset + 4) >>> 16)};
    }

    private static String parseStsdCodec(byte[] data, Box box) {
        int start = box.payloadStart();
        // version/flags(4) + entry_count(4) + entry_size(4) + format(4)
        int codecOffset = start + 12;
        if (codecOffset + 4 > box.payloadEnd()) {
            return null;
        }
        return ascii(data, codecOffset, 4);
    }

    private static long parseSampleCount(byte[] data, Box box) {
        int start = box.payloadStart();
        // version/flags(4) + sample_size(4) + sample_count(4)
        if (start + 12 > box.payloadEnd()) {
            return 0;
        }
        return u32(data, start + 8);
    }

    /**
     * 拆分连续 Box。
     */
    private static List<Box> boxes(byte[] data, int start, int end) {
        List<Box> result = new ArrayList<>();
        int offset = start;
        while (offset + 8 <= end) {
            long size = u32(data, offset);
            String type = ascii(data, offset + 4, 4);
            int headerSize = 8;
            if (size == 1) {
                if (offset + 16 > end) {
                    break;
                }
                size = u64(data, offset + 8);
                headerSize = 16;
            } else if (size == 0) {
                size = end - offset;
            }
            if (size < headerSize || offset + size > end) {
                throw invalid("MP4 Box 长度异常");
            }
            result.add(new Box(type, offset + headerSize, (int) (offset + size)));
            offset += (int) size;
        }
        return result;
    }

    private static Box find(List<Box> boxes, String type) {
        for (Box box : boxes) {
            if (type.equals(box.type())) {
                return box;
            }
        }
        return null;
    }

    private static long u32(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            throw invalid("MP4 数据不完整");
        }
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static long u64(byte[] data, int offset) {
        return (u32(data, offset) << 32) | u32(data, offset + 4);
    }

    private static String ascii(byte[] data, int offset, int length) {
        if (offset < 0 || offset + length > data.length) {
            return "";
        }
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_INVALID", message);
    }
}
