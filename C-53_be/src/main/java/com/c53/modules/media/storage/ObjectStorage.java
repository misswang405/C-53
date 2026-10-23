package com.c53.modules.media.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 对象存储抽象。
 *
 * <p>当前只有 OSS 实现。抽象的目的不是支持多后端，而是把 OSS SDK 的异常、
 * 签名与 Range 读取细节挡在业务层之外，便于测试和替换。</p>
 */
public interface ObjectStorage {

    /**
     * 写入字节数组。
     *
     * @param objectKey   对象 Key
     * @param content     内容
     * @param contentType MIME
     */
    void put(String objectKey, byte[] content, String contentType);

    /**
     * 写入流。调用方负责关闭输入流。
     *
     * @param objectKey   对象 Key
     * @param input       输入流
     * @param length      字节数
     * @param contentType MIME
     */
    void put(String objectKey, InputStream input, long length, String contentType);

    /**
     * 查询对象元数据。
     *
     * @param objectKey 对象 Key
     * @return 元数据；对象不存在时返回 {@code null}
     */
    StoredObject head(String objectKey);

    /**
     * 读取闭区间范围，用于解析 MP4 的 {@code moov}，避免下载整个视频。
     *
     * @param objectKey 对象 Key
     * @param start     起始字节，包含
     * @param end       结束字节，包含
     * @return 范围内的字节
     */
    byte[] readRange(String objectKey, long start, long end);

    /**
     * 复制对象并覆盖 Content-Type。
     *
     * @param sourceKey   源 Key
     * @param targetKey   目标 Key
     * @param contentType 目标 MIME
     */
    void copy(String sourceKey, String targetKey, String contentType);

    /**
     * 删除对象。对象不存在时视为成功。
     *
     * @param objectKey 对象 Key
     */
    void delete(String objectKey);

    /**
     * 生成下载用的签名地址。
     *
     * @param objectKey 对象 Key
     * @param ttl       有效期
     * @return 签名 URL
     */
    String signedUrl(String objectKey, Duration ttl);

    /**
     * 中止未完成的分片上传。
     *
     * @param objectKey 对象 Key
     * @param uploadId  分片上传 ID
     */
    void abortMultipartUpload(String objectKey, String uploadId);
}
