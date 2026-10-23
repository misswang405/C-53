package com.c53.modules.media.job;

import com.c53.config.MediaProperties;
import com.c53.config.PostProperties;
import com.c53.modules.media.entity.MediaAssetEntity;
import com.c53.modules.media.repository.MediaAssetRepository;
import com.c53.modules.media.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 媒体对象清理。
 *
 * <p>两类数据：超时未确认的临时上传置为 FAILED 并删除临时对象；
 * 软删除超过保留期的媒体删除 OSS 对象后移除数据库记录。</p>
 */
@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private static final int BATCH_SIZE = 200;

    private final MediaAssetRepository repository;
    private final ObjectProvider<ObjectStorage> storageProvider;
    private final PostProperties postProperties;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public MediaCleanupJob(MediaAssetRepository repository,
                           ObjectProvider<ObjectStorage> storageProvider,
                           PostProperties postProperties,
                           MediaProperties mediaProperties,
                           Clock clock) {
        this.repository = repository;
        this.storageProvider = storageProvider;
        this.postProperties = postProperties;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    /**
     * 回收过期上传与已删除媒体。
     */
    @Scheduled(fixedDelayString = "${post.cleanup.scan-interval:10m}")
    public void cleanup() {
        Instant now = clock.instant();
        expireUploads(now);
        reclaimOrphans(now);
        purgeDeleted(now);
    }

    private void expireUploads(Instant now) {
        List<MediaAssetEntity> expired = repository.findExpiredUploads(now, BATCH_SIZE);
        for (MediaAssetEntity asset : expired) {
            deleteObject(asset.getTempObjectKey());
            repository.markFailed(asset.getId());
        }
    }

    /**
     * 回收已上传但未绑定动态的孤儿对象：先删 OSS 对象，再标记删除，
     * 删除失败时保留记录交给后续的软删清理重试。
     */
    private void reclaimOrphans(Instant now) {
        Instant before = now.minus(mediaProperties.getOrphanTtl());
        List<MediaAssetEntity> orphans = repository.findStaleReady(before, BATCH_SIZE);
        for (MediaAssetEntity asset : orphans) {
            deleteObject(asset.getObjectKey());
            repository.markDeleted(asset.getId(), now);
        }
    }

    private void purgeDeleted(Instant now) {
        Instant before = now.minus(postProperties.getCleanup().getDeleteRetention());
        List<MediaAssetEntity> purgeable = repository.findPurgeable(before, BATCH_SIZE);
        for (MediaAssetEntity asset : purgeable) {
            deleteObject(asset.getObjectKey());
            deleteObject(asset.getTempObjectKey());
            repository.hardDelete(asset.getId());
        }
    }

    private void deleteObject(String key) {
        if (key == null) {
            return;
        }
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.debug("storage unavailable, skip object cleanup key={} env={}", key, mediaProperties.getEnv());
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            // 删除失败保留记录，下一轮重试
            log.warn("media object cleanup failed key={}", key, exception);
        }
    }
}
