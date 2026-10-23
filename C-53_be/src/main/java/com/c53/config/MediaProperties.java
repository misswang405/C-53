package com.c53.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * 媒体模块配置。
 *
 * <p>绑定 {@code application.yml} 中 {@code media.*} 前缀，集中管理 OSS、
 * STS、上传限制、签名有效期与限流配额。生产环境通过环境变量覆盖敏感项。</p>
 */
@Data
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    /** 环境前缀，用于对象 Key，如 dev/test/prod。 */
    private String env = "dev";

    /** OSS 访问端点，必须带协议。 */
    private String endpoint = "";

    /** OSS Region，如 oss-cn-hangzhou。 */
    private String region = "oss-cn-hangzhou";

    /** Bucket 名称。 */
    private String bucket = "";

    /** 后端 RAM 用户 AccessKeyId。 */
    private String accessKeyId = "";

    /** 后端 RAM 用户 AccessKeySecret。 */
    private String accessKeySecret = "";

    /** STS 角色 ARN。 */
    private String roleArn = "";

    /** STS 会话名称前缀。 */
    private String roleSessionName = "c53-upload";

    /** STS 服务端点。 */
    private String stsEndpoint = "sts.cn-hangzhou.aliyuncs.com";

    /** STS 的 RegionId，注意与 OSS Region 不同（去掉 oss- 前缀）。 */
    private String stsRegion = "cn-hangzhou";

    /** 直传凭证有效期。 */
    private Duration ticketTtl = Duration.ofMinutes(15);

    /** 直传分片大小。 */
    private DataSize partSize = DataSize.ofMegabytes(5);

    /** 媒体签名 URL 有效期。 */
    private Duration signedUrlTtl = Duration.ofHours(1);

    /** 临时对象保留时长。 */
    private Duration tempTtl = Duration.ofHours(24);

    /** 已上传但未绑定动态的对象保留时长，超时后回收。 */
    private Duration orphanTtl = Duration.ofHours(24);

    /** 后端上传通道允许的最大字节数。 */
    private long serverUploadMaxBytes = 10L * 1024 * 1024;

    /** 图片限制。 */
    private Image image = new Image();

    /** 视频限制。 */
    private Video video = new Video();

    /** 限流与配额。 */
    private Limits limits = new Limits();

    @Data
    public static class Image {
        /** 单张图片最大字节数。 */
        private long maxBytes = 10L * 1024 * 1024;
        /** 单条动态最多图片数。 */
        private int maxCount = 9;
        /** 源图单边最大像素。 */
        private int maxSourceEdge = 8000;
        /** 重编码后长边最大像素。 */
        private int maxOutputEdge = 2048;
        /** JPEG 输出质量。 */
        private double quality = 0.85;
    }

    @Data
    public static class Video {
        /** 单视频最大字节数。 */
        private long maxBytes = 500L * 1024 * 1024;
        /** 最长时长。 */
        private Duration maxDuration = Duration.ofMinutes(5);
        /** 最大宽度。 */
        private int maxWidth = 1920;
        /** 最大高度。 */
        private int maxHeight = 1080;
        /** 最大平均帧率。 */
        private double maxFps = 60;
        /** 单条动态最多视频数。 */
        private int maxCount = 1;
    }

    @Data
    public static class Limits {
        /** 凭证申请：每分钟。 */
        private int ticketPerMinute = 5;
        /** 凭证申请：每小时。 */
        private int ticketPerHour = 20;
        /** 后端上传：每小时。 */
        private int uploadPerHour = 30;
        /** 并发上传数。 */
        private int concurrentUploads = 3;
        /** 图片每日配额。 */
        private int imageQuotaPerDay = 200;
        /** 视频每日配额。 */
        private int videoQuotaPerDay = 10;
        /** 每日直传流量配额（字节）。 */
        private long trafficQuotaPerDayBytes = 2L * 1024 * 1024 * 1024;
        /** 详情读取：每分钟每 IP。 */
        private int readPerMinutePerIp = 120;
    }
}
