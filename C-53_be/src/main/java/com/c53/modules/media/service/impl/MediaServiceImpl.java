package com.c53.modules.media.service.impl;

import com.c53.common.ApiException;
import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.common.support.IdGenerator;
import com.c53.config.MediaProperties;
import com.c53.modules.auth.service.AuthSessionGuard;
import com.c53.modules.media.dto.MediaDtos.ConfirmRequest;
import com.c53.modules.media.dto.MediaDtos.MediaConfigResponse;
import com.c53.modules.media.dto.MediaDtos.MediaView;
import com.c53.modules.media.dto.MediaDtos.TicketResponse;
import com.c53.modules.media.dto.MediaDtos.UploadTicketRequest;
import com.c53.modules.media.entity.MediaAssetEntity;
import com.c53.modules.media.repository.MediaAssetRepository;
import com.c53.modules.media.service.MediaService;
import com.c53.modules.media.storage.ObjectStorage;
import com.c53.modules.media.storage.StoredObject;
import com.c53.modules.media.sts.StsCredentials;
import com.c53.modules.media.sts.StsTicketIssuer;
import com.c53.modules.media.support.ImageProcessor;
import com.c53.modules.media.support.MediaValidator;
import com.c53.modules.media.support.Mp4Probe;
import com.c53.modules.media.support.ObjectKeys;
import com.c53.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 媒体服务实现。
 *
 * <p>图片只走服务端上传并强制重编码；视频按大小分流：≤10MB 走服务端，
 * 10～500MB 走 STS 直传，超过 500MB 直接拒绝。</p>
 */
@Service
public class MediaServiceImpl implements MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaServiceImpl.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MediaAssetRepository repository;
    private final ObjectProvider<ObjectStorage> storageProvider;
    private final ObjectProvider<StsTicketIssuer> stsProvider;
    private final ImageProcessor imageProcessor;
    private final MediaProperties properties;
    private final AuthSessionGuard guard;
    private final RedisRateLimiter rateLimiter;
    private final Clock clock;

    public MediaServiceImpl(MediaAssetRepository repository,
                            ObjectProvider<ObjectStorage> storageProvider,
                            ObjectProvider<StsTicketIssuer> stsProvider,
                            ImageProcessor imageProcessor,
                            MediaProperties properties,
                            AuthSessionGuard guard,
                            RedisRateLimiter rateLimiter,
                            Clock clock) {
        this.repository = repository;
        this.storageProvider = storageProvider;
        this.stsProvider = stsProvider;
        this.imageProcessor = imageProcessor;
        this.properties = properties;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * 下发给前端的限制参数，前端据此决定上传入口与提示文案。
     */
    @Override
    public MediaConfigResponse config() {
        return new MediaConfigResponse(
                properties.getServerUploadMaxBytes(),
                properties.getImage().getMaxBytes(),
                properties.getVideo().getMaxBytes(),
                properties.getVideo().getMaxDuration().toSeconds(),
                properties.getImage().getMaxCount(),
                properties.getVideo().getMaxCount(),
                List.of("image/jpeg", "image/png", "image/webp"),
                List.of("video/mp4"),
                properties.getTicketTtl().toSeconds(),
                properties.getPartSize().toBytes());
    }

    /**
     * 服务端上传：图片一律走这里；视频只接受不超过直传阈值的部分。
     */
    @Override
    public MediaView uploadServer(AuthPrincipal principal, MultipartFile file, String mediaType) {
        // 会话可能已被踢下线或账号被冻结，写操作前统一校验
        guard.requireActive(principal.userId(), principal.sessionId());
        ObjectStorage storage = requireStorage();
        String type = MediaValidator.normalizeType(mediaType);
        if (type == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "mediaType 只能是 IMAGE 或 VIDEO");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "请选择要上传的文件");
        }
        long size = file.getSize();
        if ("IMAGE".equals(type)) {
            if (size > properties.getImage().getMaxBytes()) {
                throw tooLarge("图片不能超过 " + megabyte(properties.getImage().getMaxBytes()) + "MB");
            }
        } else {
            if (size > properties.getVideo().getMaxBytes()) {
                throw tooLarge("视频不能超过 " + megabyte(properties.getVideo().getMaxBytes()) + "MB");
            }
            // 大视频走 STS 直传，避免整个文件进入应用内存与网关超时
            if (size > properties.getServerUploadMaxBytes()) {
                throw ApiException.badRequest("DIRECT_UPLOAD_REQUIRED", "大于 10MB 的视频请使用直传通道");
            }
        }

        // 配额先占后用：后续任何一步失败都要回滚，避免失败请求消耗额度
        enforce("post:upload:" + principal.userId() + ":h",
                properties.getLimits().getUploadPerHour(), Duration.ofHours(1), "上传过于频繁，请稍后再试");
        enforceConcurrency(principal.userId());
        String quotaKey = quotaKey("IMAGE".equals(type) ? "image" : "video", principal.userId());
        enforceQuota(quotaKey, "IMAGE".equals(type)
                ? properties.getLimits().getImageQuotaPerDay()
                : properties.getLimits().getVideoQuotaPerDay(), "今日上传配额已用完");

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException exception) {
            rateLimiter.decrement(quotaKey);
            throw ApiException.badRequest("VALIDATION_ERROR", "读取上传文件失败");
        }
        // 按魔数判断真实类型，不信任客户端声明的 Content-Type
        MediaValidator.DetectedMedia detected;
        try {
            detected = MediaValidator.detect(raw);
            MediaValidator.requireMatches(detected, type);
        } catch (RuntimeException exception) {
            rateLimiter.decrement(quotaKey);
            throw exception;
        }

        Instant now = clock.instant();
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(IdGenerator.newId("med"));
        asset.setOwnerId(principal.userId());
        asset.setMediaType(type);
        asset.setUploadMode("SERVER");
        asset.setStorageProvider("OSS");
        asset.setBucket(properties.getBucket());
        asset.setMime(detected.mime());
        asset.setSizeBytes(size);
        asset.setSortOrder(0);
        asset.setStatus(MediaAssetRepository.INIT);
        asset.setExpiresAt(now.plus(properties.getTempTtl()));
        // 先落库占位再上传：即使进程中断，清理任务也能按 expiresAt 回收残留对象
        repository.insert(asset);
        repository.markUploading(asset.getId());

        String finalKey = null;
        try {
            byte[] content;
            String mime;
            Integer width = null;
            Integer height = null;
            Long durationMs = null;
            if ("IMAGE".equals(type)) {
                // 图片强制重编码：压缩体积、剥离 EXIF，同时消除图片马等构造型攻击载荷
                ImageProcessor.ProcessedImage processed = imageProcessor.process(raw, detected.mime());
                content = processed.bytes();
                mime = processed.mime();
                width = processed.width();
                height = processed.height();
            } else {
                // 视频不解码，只解析 MP4 头，读不到时长即视为不可信文件
                Mp4Probe.ProbeResult probe = Mp4Probe.probe(raw);
                validateVideo(probe);
                content = raw;
                mime = "video/mp4";
                width = probe.width();
                height = probe.height();
                durationMs = probe.durationMs();
            }
            finalKey = ObjectKeys.finalKey(properties.getEnv(), type, now,
                    principal.userId(), asset.getId(), mime);
            storage.put(finalKey, content, mime);
            repository.markReady(asset.getId(), finalKey, content.length, width, height, durationMs, null);
            MediaAssetEntity saved = repository.findById(asset.getId())
                    .orElseThrow(() -> new IllegalStateException("media disappeared after upload"));
            return toView(saved);
        } catch (RuntimeException exception) {
            // 上传失败要把已写入的对象和已占用的配额一起回滚
            if (finalKey != null) {
                deleteQuietly(storage, finalKey);
            }
            repository.markFailed(asset.getId());
            rateLimiter.decrement(quotaKey);
            throw exception;
        }
    }

    /**
     * 为大视频签发 STS 临时凭证，凭证的会话策略只允许写本次 uploadId 的临时前缀。
     */
    @Override
    public TicketResponse issueTicket(AuthPrincipal principal, UploadTicketRequest request) {
        guard.requireActive(principal.userId(), principal.sessionId());
        requireStorage();
        StsTicketIssuer issuer = requireSts();
        if (!"VIDEO".equals(MediaValidator.normalizeType(request.mediaType()))) {
            throw ApiException.badRequest("DIRECT_UPLOAD_UNSUPPORTED", "图片请使用服务端上传");
        }
        Long declaredSize = request.sizeBytes();
        if (declaredSize == null || declaredSize <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "sizeBytes 不合法");
        }
        // 与上传通道阈值互补：小文件走服务端，避免为几 MB 文件额外签发凭证
        if (declaredSize <= properties.getServerUploadMaxBytes()) {
            throw ApiException.badRequest("SERVER_UPLOAD_REQUIRED", "小文件请使用服务端上传");
        }
        if (declaredSize > properties.getVideo().getMaxBytes()) {
            throw tooLarge("视频不能超过 " + megabyte(properties.getVideo().getMaxBytes()) + "MB");
        }
        if (request.contentType() != null && !request.contentType().toLowerCase().startsWith("video/mp4")) {
            throw MediaValidator.unsupported();
        }

        enforce("post:ticket:" + principal.userId() + ":m",
                properties.getLimits().getTicketPerMinute(), Duration.ofMinutes(1), "操作过于频繁，请稍后再试");
        enforce("post:ticket:" + principal.userId() + ":h",
                properties.getLimits().getTicketPerHour(), Duration.ofHours(1), "申请上传凭证过于频繁");
        enforceConcurrency(principal.userId());
        String quotaKey = quotaKey("video", principal.userId());
        enforceQuota(quotaKey, properties.getLimits().getVideoQuotaPerDay(), "今日视频上传配额已用完");

        Instant now = clock.instant();
        String mediaId = IdGenerator.newId("med");
        String uploadId = IdGenerator.newId("upl");
        // 直传先落临时目录，确认通过后才复制到正式目录，未确认的对象由生命周期规则清理
        String tempKey = ObjectKeys.tempKey(properties.getEnv(), principal.userId(), uploadId, "video/mp4");

        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(mediaId);
        asset.setOwnerId(principal.userId());
        asset.setMediaType("VIDEO");
        asset.setUploadMode("DIRECT");
        asset.setStorageProvider("OSS");
        asset.setBucket(properties.getBucket());
        asset.setTempObjectKey(tempKey);
        asset.setMime("video/mp4");
        asset.setSizeBytes(declaredSize);
        asset.setSortOrder(0);
        asset.setStatus(MediaAssetRepository.INIT);
        asset.setExpiresAt(now.plus(properties.getTempTtl()));
        repository.insert(asset);

        // 记录先落库再签发凭证：凭证里的前缀必须对应一条可追踪的媒体记录
        StsCredentials credentials;
        try {
            credentials = issuer.issue(principal.userId(), uploadId);
        } catch (RuntimeException exception) {
            repository.markFailed(mediaId);
            rateLimiter.decrement(quotaKey);
            throw exception;
        }
        repository.markUploading(mediaId);

        return new TicketResponse(
                mediaId,
                uploadId,
                credentials.accessKeyId(),
                credentials.accessKeySecret(),
                credentials.securityToken(),
                credentials.expiration(),
                properties.getRegion(),
                properties.getEndpoint(),
                properties.getBucket(),
                tempKey,
                properties.getPartSize().toBytes(),
                MediaAssetRepository.UPLOADING);
    }

    /**
     * 直传确认：以 OSS 上真实存在的对象为准复核大小与 ETag，通过后转到正式目录。
     */
    @Override
    public MediaView confirm(AuthPrincipal principal, String mediaId, ConfirmRequest request) {
        guard.requireActive(principal.userId(), principal.sessionId());
        ObjectStorage storage = requireStorage();
        MediaAssetEntity asset = requireOwnedMedia(principal.userId(), mediaId);
        // 前端重试或重复回调时直接返回，保证确认接口幂等
        if (MediaAssetRepository.READY.equals(asset.getStatus())
                || MediaAssetRepository.BOUND.equals(asset.getStatus())) {
            return toView(asset);
        }
        if (!MediaAssetRepository.UPLOADING.equals(asset.getStatus())) {
            throw ApiException.conflict("MEDIA_STATE_INVALID", "媒体当前状态不能确认");
        }
        Instant now = clock.instant();
        if (asset.getExpiresAt() != null && now.isAfter(asset.getExpiresAt())) {
            repository.markFailed(mediaId);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "UPLOAD_TICKET_EXPIRED", "上传凭证已过期");
        }
        String tempKey = asset.getTempObjectKey();
        if (tempKey == null) {
            throw confirmFailed("临时对象不存在");
        }

        String finalKey = null;
        try {
            // 不信任前端上报的结果，直接读取对象元数据核对
            StoredObject stored = storage.head(tempKey);
            if (stored == null || stored.sizeBytes() <= 0) {
                throw confirmFailed("未找到上传的文件");
            }
            long declared = request != null && request.sizeBytes() != null
                    ? request.sizeBytes() : asset.getSizeBytes();
            if (stored.sizeBytes() > declared) {
                throw confirmFailed("文件大小与申请不符");
            }
            if (stored.sizeBytes() > properties.getVideo().getMaxBytes()) {
                throw tooLarge("视频不能超过 " + megabyte(properties.getVideo().getMaxBytes()) + "MB");
            }
            if (request != null && request.etag() != null
                    && !stripQuotes(request.etag()).equalsIgnoreCase(stripQuotes(stored.etag()))) {
                throw confirmFailed("文件校验失败");
            }
            Mp4Probe.ProbeResult probe = Mp4Probe.probe(storage, tempKey, stored.sizeBytes());
            validateVideo(probe);

            // 复制而非移动：复制失败时临时对象仍在，前端可重试确认
            finalKey = ObjectKeys.finalKey(properties.getEnv(), "VIDEO", now,
                    principal.userId(), mediaId, "video/mp4");
            storage.copy(tempKey, finalKey, "video/mp4");
            // 临时对象删除失败不应影响转正结果，交由生命周期规则兜底
            deleteQuietly(storage, tempKey);
            repository.markReady(mediaId, finalKey, stored.sizeBytes(),
                    probe.width(), probe.height(), probe.durationMs(), stripQuotes(stored.etag()));
            MediaAssetEntity saved = repository.findById(mediaId)
                    .orElseThrow(() -> new IllegalStateException("media disappeared after confirm"));
            return toView(saved);
        } catch (ApiException exception) {
            deleteQuietly(storage, tempKey);
            if (finalKey != null) {
                deleteQuietly(storage, finalKey);
            }
            repository.markFailed(mediaId);
            throw exception;
        } catch (RuntimeException exception) {
            deleteQuietly(storage, tempKey);
            if (finalKey != null) {
                deleteQuietly(storage, finalKey);
            }
            repository.markFailed(mediaId);
            throw confirmFailed("上传确认失败，请重新上传");
        }
    }

    @Override
    public MediaView get(AuthPrincipal principal, String mediaId) {
        return toView(requireOwnedMedia(principal.userId(), mediaId));
    }

    /**
     * 删除媒体只做软删，OSS 对象由清理任务在保留期后统一回收。
     */
    @Override
    public void delete(AuthPrincipal principal, String mediaId) {
        guard.requireActive(principal.userId(), principal.sessionId());
        MediaAssetEntity asset = requireOwnedMedia(principal.userId(), mediaId);
        if (MediaAssetRepository.DELETED.equals(asset.getStatus())) {
            return;
        }
        // 已绑定动态的媒体必须先解绑，避免动态正文里出现失效附件
        if (MediaAssetRepository.BOUND.equals(asset.getStatus())) {
            throw ApiException.conflict("MEDIA_BOUND", "媒体已绑定动态，请先在编辑中移除");
        }
        repository.markDeleted(mediaId, clock.instant());
    }

    @Override
    public List<MediaView> listByPost(String postId) {
        return repository.findByPostId(postId).stream().map(this::toView).toList();
    }

    @Override
    public Map<String, List<MediaView>> listByPosts(Collection<String> postIds) {
        Map<String, List<MediaView>> result = new HashMap<>();
        for (MediaAssetEntity asset : repository.findByPostIds(postIds)) {
            result.computeIfAbsent(asset.getPostId(), key -> new ArrayList<>()).add(toView(asset));
        }
        return result;
    }

    /**
     * 绑定前校验：媒体归属、状态可复用、图片视频不混排、数量不超限。
     *
     * @return 本条动态的媒体类型；没有媒体时返回 {@code null}
     */
    @Override
    public String prepareBinding(String ownerId, List<String> mediaIds, String currentPostId) {
        List<String> ids = dedupe(mediaIds);
        if (ids.isEmpty()) {
            return null;
        }
        List<MediaAssetEntity> assets = repository.findByOwnerAndIds(ownerId, ids);
        if (assets.size() != ids.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或不属于当前用户");
        }
        String type = null;
        int images = 0;
        int videos = 0;
        for (MediaAssetEntity asset : assets) {
            // 已属于当前动态的媒体允许在编辑时重复提交，其余只接受未绑定的 READY 状态
            boolean reusable = MediaAssetRepository.READY.equals(asset.getStatus())
                    || (MediaAssetRepository.BOUND.equals(asset.getStatus())
                    && currentPostId != null && currentPostId.equals(asset.getPostId()));
            if (!reusable) {
                throw ApiException.conflict("MEDIA_NOT_READY", "媒体已失效，请重新上传");
            }
            if (type == null) {
                type = asset.getMediaType();
            } else if (!type.equals(asset.getMediaType())) {
                throw ApiException.badRequest("MEDIA_TYPE_MIXED", "图片和视频不能混排");
            }
            if ("VIDEO".equals(asset.getMediaType())) {
                videos++;
            } else {
                images++;
            }
        }
        if (images > properties.getImage().getMaxCount()) {
            throw ApiException.badRequest("MEDIA_TOO_MANY", "最多上传 " + properties.getImage().getMaxCount() + " 张图片");
        }
        if (videos > properties.getVideo().getMaxCount()) {
            throw ApiException.badRequest("MEDIA_TOO_MANY", "每条动态最多 " + properties.getVideo().getMaxCount() + " 个视频");
        }
        return type;
    }

    @Override
    public void bind(String ownerId, String postId, List<String> mediaIds) {
        List<String> ids = dedupe(mediaIds);
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            // 已绑定的媒体只需更新排序，未绑定的才执行真正的绑定
            int updated = repository.updateSortOrder(id, ownerId, postId, index);
            if (updated == 0) {
                updated = repository.bind(id, ownerId, postId, index);
            }
            if (updated == 0) {
                throw ApiException.conflict("MEDIA_NOT_READY", "媒体已失效，请重新上传");
            }
        }
    }

    @Override
    public void release(String ownerId, Collection<String> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        for (String mediaId : mediaIds) {
            repository.unbind(mediaId, ownerId);
        }
    }

    @Override
    public void deleteByPost(String postId, Instant deletedAt) {
        repository.markDeletedByPost(postId, deletedAt);
    }

    /**
     * 视频公共校验。
     */
    private void validateVideo(Mp4Probe.ProbeResult probe) {
        if (probe.durationMs() > properties.getVideo().getMaxDuration().toMillis()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_TOO_LONG",
                    "视频不能超过 " + properties.getVideo().getMaxDuration().toMinutes() + " 分钟");
        }
        int width = probe.width();
        int height = probe.height();
        boolean within = (width <= properties.getVideo().getMaxWidth()
                && height <= properties.getVideo().getMaxHeight())
                || (height <= properties.getVideo().getMaxWidth()
                && width <= properties.getVideo().getMaxHeight());
        if (!within) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_RESOLUTION_TOO_LARGE",
                    "视频分辨率不能超过 " + properties.getVideo().getMaxWidth()
                            + "×" + properties.getVideo().getMaxHeight());
        }
        if (probe.fps() > properties.getVideo().getMaxFps() + 0.5) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_FPS_TOO_HIGH",
                    "视频帧率不能超过 " + (int) properties.getVideo().getMaxFps() + "fps");
        }
    }

    private MediaAssetEntity requireOwnedMedia(String userId, String mediaId) {
        MediaAssetEntity asset = repository.findById(mediaId)
                .filter(item -> !MediaAssetRepository.DELETED.equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在"));
        if (!userId.equals(asset.getOwnerId())) {
            throw ApiException.forbidden("FORBIDDEN", "无权访问该媒体");
        }
        return asset;
    }

    private ObjectStorage requireStorage() {
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw ossUnavailable();
        }
        return storage;
    }

    private StsTicketIssuer requireSts() {
        StsTicketIssuer issuer = stsProvider.getIfAvailable();
        if (issuer == null) {
            throw ossUnavailable();
        }
        return issuer;
    }

    private ApiException ossUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OSS_UNAVAILABLE", "文件服务暂不可用");
    }

    private void enforceConcurrency(String userId) {
        if (repository.countInFlight(userId) >= properties.getLimits().getConcurrentUploads()) {
            throw ApiException.tooManyRequests("CONCURRENT_UPLOAD_LIMIT", "同时上传的文件过多，请稍后再试",
                    Map.of("retryAfter", 5));
        }
    }

    private void enforceQuota(String key, int limit, String message) {
        RateLimitResult result = rateLimiter.tryAcquire(key, limit, secondsUntilTomorrow());
        if (!result.allowed()) {
            throw ApiException.tooManyRequests("QUOTA_EXCEEDED", message,
                    Map.of("retryAfter", result.retryAfterSeconds()));
        }
    }

    private void enforce(String key, int limit, Duration window, String message) {
        RateLimitResult result = rateLimiter.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw ApiException.tooManyRequests("RATE_LIMITED", message,
                    Map.of("retryAfter", result.retryAfterSeconds()));
        }
    }

    /**
     * 配额按 UTC 自然日计数，key 里带日期，跨天自动使用新 key。
     */
    private String quotaKey(String kind, String userId) {
        return "post:quota:" + kind + ":" + userId + ":" + LocalDate.now(clock).format(DAY);
    }

    /**
     * 配额 key 的过期时间对齐到次日的 UTC 零点，避免无效计数长期占用 Redis。
     */
    private Duration secondsUntilTomorrow() {
        Instant now = clock.instant();
        Instant tomorrow = LocalDate.now(clock).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Duration.ofSeconds(Math.max(60, tomorrow.getEpochSecond() - now.getEpochSecond()));
    }

    /**
     * 私有桶不直接暴露对象，统一换成带签名的临时 URL。
     * 签名失败时只降级为无地址，不影响其他字段展示。
     */
    private MediaView toView(MediaAssetEntity asset) {
        String url = null;
        if (asset.getObjectKey() != null) {
            ObjectStorage storage = storageProvider.getIfAvailable();
            if (storage != null) {
                try {
                    url = storage.signedUrl(asset.getObjectKey(), properties.getSignedUrlTtl());
                } catch (RuntimeException exception) {
                    log.warn("sign media url failed mediaId={}", asset.getId(), exception);
                }
            }
        }
        int sortOrder = asset.getSortOrder() == null ? 0 : asset.getSortOrder();
        return new MediaView(asset.getId(), asset.getMediaType(), url,
                asset.getWidth(), asset.getHeight(), asset.getDurationMs(),
                asset.getSizeBytes(), sortOrder);
    }

    private static List<String> dedupe(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    private static ApiException tooLarge(String message) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message);
    }

    private static ApiException confirmFailed(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_CONFIRM_FAILED", message);
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return "";
        }
        return etag.replace("\"", "").trim();
    }

    private static String megabyte(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }

    private void deleteQuietly(ObjectStorage storage, String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("cleanup object failed key={}", key, exception);
        }
    }
}
