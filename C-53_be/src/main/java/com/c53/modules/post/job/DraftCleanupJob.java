package com.c53.modules.post.job;

import com.c53.config.PostProperties;
import com.c53.modules.media.service.MediaService;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 草稿与已删除动态清理。
 *
 * <p>超过保留期的草稿软删除；软删除超过保留期的动态物理删除。
 * 媒体对象由 {@code MediaCleanupJob} 独立回收。</p>
 */
@Component
public class DraftCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DraftCleanupJob.class);

    private static final int BATCH_SIZE = 200;

    private final PostRepository postRepository;
    private final MediaService mediaService;
    private final TransactionTemplate transactionTemplate;
    private final PostProperties properties;
    private final Clock clock;

    public DraftCleanupJob(PostRepository postRepository,
                           MediaService mediaService,
                           TransactionTemplate transactionTemplate,
                           PostProperties properties,
                           Clock clock) {
        this.postRepository = postRepository;
        this.mediaService = mediaService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 清理过期草稿与已删除数据。
     */
    @Scheduled(fixedDelayString = "${post.cleanup.scan-interval:PT10M}")
    public void cleanup() {
        Instant now = clock.instant();
        removeStaleDrafts(now);
        purgeDeletedPosts(now);
    }

    /**
     * 清理超过保留期的草稿，同时解除其媒体绑定，避免媒体资源被草稿长期占用。
     */
    private void removeStaleDrafts(Instant now) {
        Instant before = now.minus(properties.getDraft().getRetention());
        List<PostEntity> drafts = postRepository.findStaleDrafts(before, BATCH_SIZE);
        for (PostEntity draft : drafts) {
            try {
                transactionTemplate.executeWithoutResult(transaction -> {
                    int updated = postRepository.softDelete(draft.getId(), draft.getAuthorId(), now);
                    if (updated == 1) {
                        mediaService.deleteByPost(draft.getId(), now);
                    }
                });
            } catch (RuntimeException exception) {
                log.error("draft cleanup failed postId={}", draft.getId(), exception);
            }
        }
    }

    /**
     * 物理删除已过保留期的软删动态；单条失败只记日志，不影响同批其他记录。
     */
    private void purgeDeletedPosts(Instant now) {
        Instant before = now.minus(properties.getCleanup().getDeleteRetention());
        List<PostEntity> posts = postRepository.findPurgeable(before, BATCH_SIZE);
        for (PostEntity post : posts) {
            try {
                postRepository.hardDelete(post.getId());
            } catch (RuntimeException exception) {
                log.error("deleted post purge failed postId={}", post.getId(), exception);
            }
        }
    }
}
