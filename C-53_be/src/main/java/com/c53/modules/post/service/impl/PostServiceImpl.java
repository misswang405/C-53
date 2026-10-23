package com.c53.modules.post.service.impl;

import com.c53.common.ApiException;
import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.common.support.IdGenerator;
import com.c53.config.PostProperties;
import com.c53.modules.auth.service.AuthSessionGuard;
import com.c53.modules.auth.service.UserSummaryService;
import com.c53.modules.auth.service.UserSummaryService.UserSummary;
import com.c53.modules.media.dto.MediaDtos.MediaView;
import com.c53.modules.media.service.MediaService;
import com.c53.modules.post.dto.PostDtos.CreatePostRequest;
import com.c53.modules.post.dto.PostDtos.PostListResponse;
import com.c53.modules.post.dto.PostDtos.PostResponse;
import com.c53.modules.post.dto.PostDtos.ScheduleRequest;
import com.c53.modules.post.dto.PostDtos.UpdatePostRequest;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.repository.PostRepository;
import com.c53.modules.post.repository.PostStatus;
import com.c53.modules.post.service.PostService;
import com.c53.modules.post.support.ContentRules;
import com.c53.modules.post.support.CursorCodec;
import com.c53.modules.topic.dto.TopicDtos.TopicSummary;
import com.c53.modules.topic.service.TopicService;
import com.c53.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 发布服务实现。
 *
 * <p>事务使用 {@link TransactionTemplate} 显式控制，媒体绑定与话题计数
 * 和动态写入保持同一事务；状态流转全部依赖条件更新，防止并发覆盖。</p>
 */
@Service
public class PostServiceImpl implements PostService {

    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    private final PostRepository postRepository;
    private final MediaService mediaService;
    private final TopicService topicService;
    private final UserSummaryService userSummaryService;
    private final AuthSessionGuard guard;
    private final RedisRateLimiter rateLimiter;
    private final PostProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PostServiceImpl(PostRepository postRepository,
                           MediaService mediaService,
                           TopicService topicService,
                           UserSummaryService userSummaryService,
                           AuthSessionGuard guard,
                           RedisRateLimiter rateLimiter,
                           PostProperties properties,
                           TransactionTemplate transactionTemplate,
                           Clock clock) {
        this.postRepository = postRepository;
        this.mediaService = mediaService;
        this.topicService = topicService;
        this.userSummaryService = userSummaryService;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public CreateResult create(AuthPrincipal principal, CreatePostRequest request) {
        guard.requireActive(principal.userId(), principal.sessionId());
        String userId = principal.userId();
        String status = PostStatus.parseCreateStatus(request.status());
        String content = ContentRules.normalize(request.content());
        boolean draft = PostStatus.DRAFT.equals(status);
        if (draft) {
            ContentRules.requireDraft(content);
        } else {
            ContentRules.requirePublish(content);
            rejectBlockedWords(content);
        }

        String topicId = trimToNull(request.topicId());
        if (topicId != null) {
            topicService.requireActive(topicId);
        } else if (!draft) {
            throw ApiException.badRequest("TOPIC_REQUIRED", "请选择话题");
        }

        List<String> mediaIds = normalizeMediaIds(request.mediaIds(), request.imageIds());
        String mediaType = mediaService.prepareBinding(userId, mediaIds, null);

        Instant now = clock.instant();
        Instant scheduledAt = null;
        if (PostStatus.SCHEDULED.equals(status)) {
            scheduledAt = validateScheduledAt(request.scheduledAt(), now);
        } else if (request.scheduledAt() != null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "只有定时发布才能设置 scheduledAt");
        }

        if (draft) {
            enforce("post:autosave:" + userId + ":m", properties.getLimits().getAutosavePerMinute(),
                    Duration.ofMinutes(1), "保存过于频繁，请稍后再试");
            if (postRepository.countDrafts(userId) >= properties.getDraft().getMaxPerUser()) {
                throw ApiException.conflict("DRAFT_LIMIT_REACHED",
                        "草稿数量已达上限 " + properties.getDraft().getMaxPerUser() + "，请先清理旧草稿");
            }
        } else {
            enforce("post:publish:" + userId + ":h", properties.getLimits().getPublishPerHour(),
                    Duration.ofHours(1), "发布过于频繁，请稍后再试");
        }

        String clientRequestId = normalizeRequestId(request.clientRequestId());
        if (clientRequestId != null) {
            Optional<PostEntity> existing = postRepository.findByAuthorAndRequestId(userId, clientRequestId);
            if (existing.isPresent()) {
                if (existing.get().getDeletedAt() != null) {
                    throw ApiException.conflict("CLIENT_REQUEST_ID_REUSED", "请求标识已被使用，请刷新后重试");
                }
                return new CreateResult(toResponses(List.of(existing.get()), principal).get(0), false);
            }
        }

        PostEntity entity = new PostEntity();
        entity.setId(IdGenerator.newId("pst"));
        entity.setAuthorId(userId);
        entity.setStatus(status);
        entity.setContent(content);
        entity.setTopicId(topicId);
        entity.setMediaType(mediaType);
        entity.setScheduledAt(scheduledAt);
        entity.setPublishedAt(PostStatus.PUBLISHED.equals(status) ? now : null);
        entity.setVersion(1);
        entity.setClientRequestId(clientRequestId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            transactionTemplate.executeWithoutResult(transaction -> {
                postRepository.insert(entity);
                if (!mediaIds.isEmpty()) {
                    mediaService.bind(userId, entity.getId(), mediaIds);
                }
                if (PostStatus.PUBLISHED.equals(status)) {
                    topicService.adjustPostCount(topicId, 1);
                }
            });
        } catch (DuplicateKeyException exception) {
            PostEntity existing = postRepository.findByAuthorAndRequestId(userId, clientRequestId)
                    .orElseThrow(() -> exception);
            return new CreateResult(toResponses(List.of(existing), principal).get(0), false);
        }

        return new CreateResult(loadView(entity.getId(), principal), true);
    }

    @Override
    public PostResponse get(AuthPrincipal principal, String id) {
        PostEntity post = postRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> !PostStatus.DELETED.equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在"));
        if (!PostStatus.PUBLISHED.equals(post.getStatus())) {
            // 草稿和定时内容对他人不可见，统一返回 404 避免暴露存在性
            if (principal == null || !principal.userId().equals(post.getAuthorId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在");
            }
        }
        return toResponses(List.of(post), principal).get(0);
    }

    @Override
    public PostResponse update(AuthPrincipal principal, String id, UpdatePostRequest request) {
        guard.requireActive(principal.userId(), principal.sessionId());
        String userId = principal.userId();
        PostEntity post = requireOwned(principal, id);

        boolean published = PostStatus.PUBLISHED.equals(post.getStatus());
        boolean scheduled = PostStatus.SCHEDULED.equals(post.getStatus());
        String content = ContentRules.normalize(request.content());
        if (published || scheduled) {
            ContentRules.requirePublish(content);
            rejectBlockedWords(content);
        } else {
            ContentRules.requireDraft(content);
        }

        String topicId = trimToNull(request.topicId());
        if (topicId != null) {
            topicService.requireActive(topicId);
        } else if (published || scheduled) {
            throw ApiException.badRequest("TOPIC_REQUIRED", "请选择话题");
        }

        List<String> mediaIds = normalizeMediaIds(request.mediaIds(), request.imageIds());
        String mediaType = mediaService.prepareBinding(userId, mediaIds, id);
        int version = request.version();
        Instant now = clock.instant();
        String previousTopicId = post.getTopicId();
        List<String> removed = new ArrayList<>();

        transactionTemplate.executeWithoutResult(transaction -> {
            int updated = postRepository.updateContent(id, userId, version, content, topicId, mediaType,
                    published ? now : null);
            if (updated == 0) {
                throw versionConflict(id, principal, version);
            }
            Set<String> keep = new LinkedHashSet<>(mediaIds);
            for (MediaView view : mediaService.listByPost(id)) {
                if (!keep.contains(view.mediaId())) {
                    removed.add(view.mediaId());
                }
            }
            mediaService.release(userId, removed);
            if (!mediaIds.isEmpty()) {
                mediaService.bind(userId, id, mediaIds);
            }
            if (published && !Objects.equals(previousTopicId, topicId)) {
                topicService.adjustPostCount(previousTopicId, -1);
                topicService.adjustPostCount(topicId, 1);
            }
        });

        return loadView(id, principal);
    }

    @Override
    public PostResponse publish(AuthPrincipal principal, String id) {
        guard.requireActive(principal.userId(), principal.sessionId());
        String userId = principal.userId();
        PostEntity post = requireOwned(principal, id);
        if (!PostStatus.DRAFT.equals(post.getStatus())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "只有草稿可以立即发布");
        }
        ContentRules.requirePublish(post.getContent());
        rejectBlockedWords(post.getContent());
        String topicId = post.getTopicId();
        topicService.requireActive(topicId);
        List<MediaView> media = mediaService.listByPost(id);
        String mediaType = media.isEmpty() ? null : media.get(0).mediaType();
        mediaService.prepareBinding(userId, media.stream().map(MediaView::mediaId).toList(), id);

        enforce("post:publish:" + userId + ":h", properties.getLimits().getPublishPerHour(),
                Duration.ofHours(1), "发布过于频繁，请稍后再试");

        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(transaction -> {
            int updated = postRepository.publishDraft(id, userId, now);
            if (updated == 0) {
                throw ApiException.conflict("INVALID_STATUS_TRANSITION", "动态状态已变化，请刷新后重试");
            }
            if (mediaType != null && post.getMediaType() != null && !mediaType.equals(post.getMediaType())) {
                // 媒体类型在草稿阶段已校验，这里仅兜底保持一致性
                log.warn("media type mismatch postId={} stored={} actual={}", id, post.getMediaType(), mediaType);
            }
            topicService.adjustPostCount(topicId, 1);
        });
        return loadView(id, principal);
    }

    @Override
    public PostResponse schedule(AuthPrincipal principal, String id, ScheduleRequest request) {
        guard.requireActive(principal.userId(), principal.sessionId());
        String userId = principal.userId();
        PostEntity post = requireOwned(principal, id);
        boolean wasScheduled = PostStatus.SCHEDULED.equals(post.getStatus());
        if (!PostStatus.DRAFT.equals(post.getStatus()) && !wasScheduled) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "当前状态不能设置定时发布");
        }
        ContentRules.requirePublish(post.getContent());
        rejectBlockedWords(post.getContent());
        String topicId = post.getTopicId();
        topicService.requireActive(topicId);
        List<String> mediaIds = mediaService.listByPost(id).stream().map(MediaView::mediaId).toList();
        mediaService.prepareBinding(userId, mediaIds, id);

        Instant now = clock.instant();
        Instant scheduledAt = validateScheduledAt(request.scheduledAt(), now);
        if (wasScheduled) {
            enforce("post:autosave:" + userId + ":m", properties.getLimits().getAutosavePerMinute(),
                    Duration.ofMinutes(1), "操作过于频繁，请稍后再试");
        } else {
            enforce("post:publish:" + userId + ":h", properties.getLimits().getPublishPerHour(),
                    Duration.ofHours(1), "发布过于频繁，请稍后再试");
        }

        transactionTemplate.executeWithoutResult(transaction -> {
            int updated = postRepository.schedule(id, userId, scheduledAt);
            if (updated == 0) {
                throw ApiException.conflict("INVALID_STATUS_TRANSITION", "动态状态已变化，请刷新后重试");
            }
            if (!mediaIds.isEmpty()) {
                mediaService.bind(userId, id, mediaIds);
            }
        });
        return loadView(id, principal);
    }

    @Override
    public PostResponse cancelSchedule(AuthPrincipal principal, String id) {
        guard.requireActive(principal.userId(), principal.sessionId());
        requireOwned(principal, id);
        int updated = postRepository.cancelSchedule(id, principal.userId());
        if (updated == 0) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "只有定时中的动态可以取消定时");
        }
        return loadView(id, principal);
    }

    @Override
    public void delete(AuthPrincipal principal, String id) {
        guard.requireActive(principal.userId(), principal.sessionId());
        Optional<PostEntity> optional = postRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null);
        if (optional.isEmpty()) {
            return;
        }
        PostEntity post = optional.get();
        if (!principal.userId().equals(post.getAuthorId())) {
            throw ApiException.forbidden("FORBIDDEN", "只能删除自己的动态");
        }
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(transaction -> {
            int updated = postRepository.softDelete(id, principal.userId(), now);
            if (updated == 0) {
                return;
            }
            mediaService.deleteByPost(id, now);
            if (PostStatus.PUBLISHED.equals(post.getStatus())) {
                topicService.adjustPostCount(post.getTopicId(), -1);
            }
        });
    }

    @Override
    public PostListResponse myPosts(AuthPrincipal principal, String status, String cursor, int limit) {
        guard.requireActive(principal.userId(), principal.sessionId());
        String normalizedStatus = normalizeListStatus(status);
        int size = limit <= 0 ? 20 : Math.min(limit, 50);
        CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
        List<PostEntity> rows = postRepository.findMine(principal.userId(), normalizedStatus,
                decoded == null ? null : decoded.time(),
                decoded == null ? null : decoded.id(),
                size + 1);
        boolean hasMore = rows.size() > size;
        List<PostEntity> page = hasMore ? rows.subList(0, size) : rows;
        List<PostResponse> items = toResponses(page, principal);
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            PostEntity last = page.get(page.size() - 1);
            Instant time = PostStatus.PUBLISHED.equals(normalizedStatus)
                    ? last.getPublishedAt() : last.getUpdatedAt();
            nextCursor = CursorCodec.encode(time, last.getId());
        }
        return new PostListResponse(items, nextCursor);
    }

    private PostEntity requireOwned(AuthPrincipal principal, String id) {
        PostEntity post = postRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> !PostStatus.DELETED.equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在"));
        if (!principal.userId().equals(post.getAuthorId())) {
            throw ApiException.forbidden("FORBIDDEN", "只能操作自己的动态");
        }
        return post;
    }

    private ApiException versionConflict(String id, AuthPrincipal principal, int version) {
        PostEntity current = postRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在"));
        if (!principal.userId().equals(current.getAuthorId())) {
            throw ApiException.forbidden("FORBIDDEN", "只能操作自己的动态");
        }
        int currentVersion = current.getVersion() == null ? version + 1 : current.getVersion();
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "内容已在其他位置更新，请刷新后重试", Map.of("currentVersion", currentVersion));
    }

    private PostResponse loadView(String id, AuthPrincipal principal) {
        PostEntity post = postRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在"));
        return toResponses(List.of(post), principal).get(0);
    }

    private List<PostResponse> toResponses(List<PostEntity> posts, AuthPrincipal principal) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<String> authorIds = new HashSet<>();
        Set<String> topicIds = new HashSet<>();
        Set<String> postIds = new HashSet<>();
        for (PostEntity post : posts) {
            authorIds.add(post.getAuthorId());
            if (post.getTopicId() != null) {
                topicIds.add(post.getTopicId());
            }
            postIds.add(post.getId());
        }
        Map<String, UserSummary> users = userSummaryService.findByIds(authorIds);
        Map<String, TopicSummary> topics = topicService.findByIds(topicIds);
        Map<String, List<MediaView>> media = new HashMap<>(mediaService.listByPosts(postIds));

        List<PostResponse> result = new ArrayList<>(posts.size());
        for (PostEntity post : posts) {
            result.add(new PostResponse(
                    post.getId(),
                    users.get(post.getAuthorId()),
                    post.getTopicId() == null ? null : topics.get(post.getTopicId()),
                    post.getContent(),
                    ContentRules.summary(post.getContent()),
                    post.getMediaType(),
                    media.getOrDefault(post.getId(), List.of()),
                    post.getStatus(),
                    post.getScheduledAt(),
                    post.getPublishedAt(),
                    post.getEditedAt(),
                    post.getCreatedAt(),
                    post.getVersion() == null ? 1 : post.getVersion(),
                    principal != null && principal.userId().equals(post.getAuthorId())));
        }
        return result;
    }

    private Instant validateScheduledAt(Instant scheduledAt, Instant now) {
        if (scheduledAt == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "请选择发布时间");
        }
        Instant earliest = now.plus(properties.getSchedule().getMinLead());
        Instant latest = now.plus(properties.getSchedule().getMaxLead());
        if (scheduledAt.isBefore(earliest) || scheduledAt.isAfter(latest)) {
            throw ApiException.badRequest("SCHEDULE_OUT_OF_RANGE",
                    "发布时间需在 " + properties.getSchedule().getMinLead().toMinutes()
                            + " 分钟后、一年内");
        }
        return scheduledAt;
    }

    private void rejectBlockedWords(String content) {
        if (properties.getBlockedWords() == null || properties.getBlockedWords().isEmpty()) {
            return;
        }
        String lower = content.toLowerCase();
        for (String word : properties.getBlockedWords()) {
            if (word != null && !word.isBlank() && lower.contains(word.toLowerCase())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_REJECTED",
                        "内容包含不适宜发布的词语");
            }
        }
    }

    private void enforce(String key, int limit, Duration window, String message) {
        RateLimitResult result = rateLimiter.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw ApiException.tooManyRequests("RATE_LIMITED", message,
                    Map.of("retryAfter", result.retryAfterSeconds()));
        }
    }

    private static String normalizeListStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case PostStatus.DRAFT, PostStatus.SCHEDULED, PostStatus.PUBLISHED -> normalized;
            default -> throw ApiException.badRequest("VALIDATION_ERROR", "不支持的动态状态");
        };
    }

    private static List<String> normalizeMediaIds(List<String> mediaIds, List<String> imageIds) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (mediaIds != null) {
            for (String id : mediaIds) {
                if (id != null && !id.isBlank()) {
                    unique.add(id.trim());
                }
            }
        }
        if (imageIds != null) {
            for (String id : imageIds) {
                if (id != null && !id.isBlank()) {
                    unique.add(id.trim());
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeRequestId(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > 64) {
            throw ApiException.badRequest("VALIDATION_ERROR", "clientRequestId 不能超过 64 个字符");
        }
        return trimmed;
    }
}
