package com.c53.modules.media.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.c53.common.DependencyUnavailableException;
import com.c53.config.MediaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

/**
 * {@link ObjectStorage} 的阿里云 OSS 实现。
 *
 * <p>所有异常都包装成 {@link DependencyUnavailableException}，由上层统一转成 503，
 * 避免把 OSS 的原始错误码暴露给客户端。</p>
 */
@Component
@ConditionalOnExpression("'${media.bucket:}' != ''")
public class OssObjectStorage implements ObjectStorage {

    private final OSS ossClient;
    private final String bucket;

    public OssObjectStorage(OSS ossClient, MediaProperties properties) {
        this.ossClient = ossClient;
        this.bucket = properties.getBucket();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        try {
            ossClient.putObject(bucket, objectKey, new ByteArrayInputStream(content), metadata);
        } catch (RuntimeException exception) {
            throw unavailable("写入对象失败", exception);
        }
    }

    /**
     * 流式写入。长度必须由调用方给出，否则 OSS SDK 会把整个流读进内存再上传。
     */
    @Override
    public void put(String objectKey, InputStream input, long length, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(length);
        metadata.setContentType(contentType);
        try {
            ossClient.putObject(bucket, objectKey, input, metadata);
        } catch (RuntimeException exception) {
            throw unavailable("写入对象失败", exception);
        }
    }

    /**
     * 读取对象元数据。对象不存在时返回 {@code null} 而不是抛异常，
     * 让「前端还没传完」这种正常分支不必走异常流程。
     */
    @Override
    public StoredObject head(String objectKey) {
        try {
            ObjectMetadata metadata = ossClient.getObjectMetadata(bucket, objectKey);
            return new StoredObject(objectKey, metadata.getContentLength(),
                    metadata.getContentType(), metadata.getETag());
        } catch (OSSException exception) {
            if ("NoSuchKey".equals(exception.getErrorCode())
                    || "NoSuchObject".equals(exception.getErrorCode())) {
                return null;
            }
            throw unavailable("读取对象元数据失败", exception);
        } catch (RuntimeException exception) {
            throw unavailable("读取对象元数据失败", exception);
        }
    }

    /**
     * 按字节区间读取，用于解析 MP4 头部而不下载整个视频。
     */
    @Override
    public byte[] readRange(String objectKey, long start, long end) {
        GetObjectRequest request = new GetObjectRequest(bucket, objectKey);
        request.setRange(start, end);
        try (OSSObject object = ossClient.getObject(request);
             InputStream input = object.getObjectContent()) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw unavailable("读取对象范围失败", exception);
        }
    }

    /**
     * 服务端复制，用于直传视频从临时目录转到正式目录，数据不经过应用。
     */
    @Override
    public void copy(String sourceKey, String targetKey, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        CopyObjectRequest request = new CopyObjectRequest(bucket, sourceKey, bucket, targetKey);
        request.setNewObjectMetadata(metadata);
        try {
            ossClient.copyObject(request);
        } catch (RuntimeException exception) {
            throw unavailable("复制对象失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            ossClient.deleteObject(bucket, objectKey);
        } catch (RuntimeException exception) {
            throw unavailable("删除对象失败", exception);
        }
    }

    /**
     * 生成带签名的临时访问地址。桶是私有的，前端只能用这种方式取图/取视频。
     */
    @Override
    public String signedUrl(String objectKey, Duration ttl) {
        Date expiration = new Date(System.currentTimeMillis() + ttl.toMillis());
        try {
            return ossClient.generatePresignedUrl(bucket, objectKey, expiration).toString();
        } catch (RuntimeException exception) {
            throw unavailable("生成签名地址失败", exception);
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        try {
            ossClient.abortMultipartUpload(
                    new com.aliyun.oss.model.AbortMultipartUploadRequest(bucket, objectKey, uploadId));
        } catch (RuntimeException exception) {
            throw unavailable("中止分片上传失败", exception);
        }
    }

    private DependencyUnavailableException unavailable(String message, Exception cause) {
        return new DependencyUnavailableException(message, cause);
    }
}
