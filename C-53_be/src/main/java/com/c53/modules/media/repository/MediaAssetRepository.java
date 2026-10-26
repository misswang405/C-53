package com.c53.modules.media.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.modules.media.entity.MediaAssetEntity;
import com.c53.modules.media.mapper.MediaAssetMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 媒体资源数据访问。
 *
 * <p>状态变更全部使用条件更新，确保“只有 READY 且未绑定的媒体才能绑定”
 * 这类规则在并发下仍然成立。</p>
 */
@Repository
public class MediaAssetRepository {

    // 媒体状态流转：INIT → UPLOADING → READY → BOUND；失败进 FAILED，删除进 DELETED
    public static final String INIT = "INIT";
    public static final String UPLOADING = "UPLOADING";
    public static final String READY = "READY";
    public static final String BOUND = "BOUND";
    public static final String FAILED = "FAILED";
    public static final String DELETED = "DELETED";

    private final MediaAssetMapper mapper;

    public MediaAssetRepository(MediaAssetMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<MediaAssetEntity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<MediaAssetEntity> findByOwnerAndIds(String ownerId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<MediaAssetEntity>lambdaQuery()
                .eq(MediaAssetEntity::getOwnerId, ownerId)
                .in(MediaAssetEntity::getId, ids));
    }

    public List<MediaAssetEntity> findByPostId(String postId) {
        return mapper.selectList(Wrappers.<MediaAssetEntity>lambdaQuery()
                .eq(MediaAssetEntity::getPostId, postId)
                .ne(MediaAssetEntity::getStatus, DELETED)
                .orderByAsc(MediaAssetEntity::getSortOrder));
    }

    public List<MediaAssetEntity> findByPostIds(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<MediaAssetEntity>lambdaQuery()
                .in(MediaAssetEntity::getPostId, postIds)
                .ne(MediaAssetEntity::getStatus, DELETED)
                .orderByAsc(MediaAssetEntity::getSortOrder));
    }

    public List<MediaAssetEntity> findExpiredUploads(Instant now, int limit) {
        return mapper.selectList(Wrappers.<MediaAssetEntity>lambdaQuery()
                .in(MediaAssetEntity::getStatus, INIT, UPLOADING)
                .isNotNull(MediaAssetEntity::getExpiresAt)
                .le(MediaAssetEntity::getExpiresAt, now)
                .orderByAsc(MediaAssetEntity::getExpiresAt)
                .last("LIMIT " + limit));
    }

    /**
     * 软删除且已过保留期的媒体，由清理任务删除 OSS 对象后移除记录。
     */
    public List<MediaAssetEntity> findPurgeable(Instant before, int limit) {
        return mapper.selectList(Wrappers.<MediaAssetEntity>lambdaQuery()
                .eq(MediaAssetEntity::getStatus, DELETED)
                .le(MediaAssetEntity::getDeletedAt, before)
                .orderByAsc(MediaAssetEntity::getDeletedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查找已上传成功但长时间未绑定动态的媒体，用于回收孤儿对象。
     */
    public List<MediaAssetEntity> findStaleReady(Instant before, int limit) {
        return mapper.selectList(Wrappers.<MediaAssetEntity>lambdaQuery()
                .eq(MediaAssetEntity::getStatus, READY)
                .isNull(MediaAssetEntity::getPostId)
                .le(MediaAssetEntity::getCreatedAt, before)
                .orderByAsc(MediaAssetEntity::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 统计进行中的上传，用于并发上传数限制。
     */
    public long countInFlight(String ownerId) {
        Long count = mapper.selectCount(Wrappers.<MediaAssetEntity>lambdaQuery()
                .eq(MediaAssetEntity::getOwnerId, ownerId)
                .in(MediaAssetEntity::getStatus, INIT, UPLOADING));
        return count == null ? 0 : count;
    }

    public void insert(MediaAssetEntity entity) {
        mapper.insert(entity);
    }

    public int markUploading(String id) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getStatus, UPLOADING)
                .eq(MediaAssetEntity::getId, id)
                .eq(MediaAssetEntity::getStatus, INIT));
    }

    /**
     * 转为可用状态。同时清空临时 key 与过期时间，表示这条记录不再需要被清理。
     */
    public int markReady(String id, String objectKey, long sizeBytes, Integer width,
                         Integer height, Long durationMs, String checksum) {
        LambdaUpdateWrapper<MediaAssetEntity> wrapper = Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getStatus, READY)
                .set(MediaAssetEntity::getObjectKey, objectKey)
                .set(MediaAssetEntity::getTempObjectKey, null)
                .set(MediaAssetEntity::getSizeBytes, sizeBytes)
                .set(MediaAssetEntity::getChecksum, checksum)
                .set(MediaAssetEntity::getExpiresAt, null)
                .eq(MediaAssetEntity::getId, id)
                .eq(MediaAssetEntity::getStatus, UPLOADING);
        if (width != null) {
            wrapper.set(MediaAssetEntity::getWidth, width);
        }
        if (height != null) {
            wrapper.set(MediaAssetEntity::getHeight, height);
        }
        if (durationMs != null) {
            wrapper.set(MediaAssetEntity::getDurationMs, durationMs);
        }
        return mapper.update(null, wrapper);
    }

    public int markFailed(String id) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getStatus, FAILED)
                .eq(MediaAssetEntity::getId, id)
                .in(MediaAssetEntity::getStatus, INIT, UPLOADING));
    }

    /**
     * 软删除，已删除的记录重复调用不会刷新删除时间。
     */
    public int markDeleted(String id, Instant deletedAt) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getStatus, DELETED)
                .set(MediaAssetEntity::getDeletedAt, deletedAt)
                .eq(MediaAssetEntity::getId, id)
                .ne(MediaAssetEntity::getStatus, DELETED));
    }

    public int markDeletedByPost(String postId, Instant deletedAt) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getStatus, DELETED)
                .set(MediaAssetEntity::getDeletedAt, deletedAt)
                .eq(MediaAssetEntity::getPostId, postId)
                .ne(MediaAssetEntity::getStatus, DELETED));
    }

    /**
     * 绑定一个媒体到动态。
     *
     * @return 影响行数，0 表示媒体不可用或已被其他动态绑定
     */
    public int bind(String id, String ownerId, String postId, int sortOrder) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getPostId, postId)
                .set(MediaAssetEntity::getSortOrder, sortOrder)
                .set(MediaAssetEntity::getStatus, BOUND)
                .eq(MediaAssetEntity::getId, id)
                .eq(MediaAssetEntity::getOwnerId, ownerId)
                .eq(MediaAssetEntity::getStatus, READY)
                .isNull(MediaAssetEntity::getPostId));
    }

    public int updateSortOrder(String id, String ownerId, String postId, int sortOrder) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getSortOrder, sortOrder)
                .eq(MediaAssetEntity::getId, id)
                .eq(MediaAssetEntity::getOwnerId, ownerId)
                .eq(MediaAssetEntity::getPostId, postId)
                .eq(MediaAssetEntity::getStatus, BOUND));
    }

    /**
     * 解绑后回到 READY，可再次用于其他动态；对象本身保留。
     */
    public int unbind(String id, String ownerId) {
        return mapper.update(null, Wrappers.<MediaAssetEntity>lambdaUpdate()
                .set(MediaAssetEntity::getPostId, null)
                .set(MediaAssetEntity::getSortOrder, 0)
                .set(MediaAssetEntity::getStatus, READY)
                .eq(MediaAssetEntity::getId, id)
                .eq(MediaAssetEntity::getOwnerId, ownerId)
                .eq(MediaAssetEntity::getStatus, BOUND));
    }

    /**
     * 物理删除记录。调用前必须已删除对应的 OSS 对象，否则会留下无法追踪的孤儿文件。
     */
    public void hardDelete(String id) {
        mapper.deleteById(id);
    }
}
