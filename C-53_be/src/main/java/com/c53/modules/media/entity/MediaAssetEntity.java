package com.c53.modules.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 媒体资源实体，对应 {@code media_assets}。
 *
 * <p>图片和视频对象存阿里云 OSS，本表只保存元数据。
 * 直传时先写临时 Key，确认后复制到正式 Key 并清空临时 Key。</p>
 */
@Data
@TableName("media_assets")
public class MediaAssetEntity {

    /** 主键，{@code med_} + ULID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 上传者用户 ID。 */
    private String ownerId;

    /** 媒体类型：IMAGE/VIDEO。 */
    private String mediaType;

    /** 上传模式：SERVER/DIRECT。 */
    private String uploadMode;

    /** 存储提供方，当前固定 OSS。 */
    private String storageProvider;

    /** OSS Bucket。 */
    private String bucket;

    /** 正式对象 Key。 */
    private String objectKey;

    /** 直传临时对象 Key。 */
    private String tempObjectKey;

    /** MIME 类型。 */
    private String mime;

    /** 实际字节数。 */
    private Long sizeBytes;

    /** 像素宽。 */
    private Integer width;

    /** 像素高。 */
    private Integer height;

    /** 视频时长（毫秒）。 */
    private Long durationMs;

    /** ETag 或校验和。 */
    private String checksum;

    /** 绑定的动态 ID。 */
    private String postId;

    /** 动态内展示顺序。 */
    private Integer sortOrder;

    /** 状态：INIT/UPLOADING/READY/BOUND/FAILED/DELETED。 */
    private String status;

    /** 凭证或临时对象过期时间。 */
    private Instant expiresAt;

    /** 创建时间。 */
    private Instant createdAt;

    /** 更新时间。 */
    private Instant updatedAt;

    /** 清理时间。 */
    private Instant deletedAt;
}
