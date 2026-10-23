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
