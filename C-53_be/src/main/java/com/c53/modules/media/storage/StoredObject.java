package com.c53.modules.media.storage;

/**
 * 对象元数据。
 *
 * @param key         对象 Key
 * @param sizeBytes   字节数
 * @param contentType MIME
 * @param etag        ETag，可能带引号
 */
public record StoredObject(String key, long sizeBytes, String contentType, String etag) {
}
