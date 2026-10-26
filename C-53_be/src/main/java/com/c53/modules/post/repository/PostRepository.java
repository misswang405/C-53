package com.c53.modules.post.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.mapper.PostMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 动态数据访问。
 *
 * <p>所有查询显式过滤 {@code deleted_at}；状态流转使用条件更新，
 * 避免并发下重复发布或覆盖他人修改。</p>
 */
@Repository
public class PostRepository {

    private final PostMapper mapper;

    public PostRepository(PostMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<PostEntity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public int incrementLike(String id) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .setSql("like_count = like_count + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt));
    }

    public int decrementLike(String id) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .setSql("like_count = GREATEST(0, like_count - 1)")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt));
    }

    public int incrementComment(String id) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .setSql("comment_count = comment_count + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt));
    }

    public List<PostEntity> findPublishedByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<PostEntity>lambdaQuery()
                .in(PostEntity::getId, ids)
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt)
                .isNotNull(PostEntity::getPublishedAt));
    }

    public Optional<PostEntity> findByAuthorAndRequestId(String authorId, String clientRequestId) {
        if (clientRequestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getAuthorId, authorId)
                .eq(PostEntity::getClientRequestId, clientRequestId)));
    }

    public void insert(PostEntity entity) {
        mapper.insert(entity);
    }

    /**
     * 按版本号更新正文、话题与媒体类型。
     *
     * @return 影响行数，0 表示版本冲突或无权修改
     */
    public int updateContent(String id, String authorId, int version, String content,
                             String topicId, String mediaType, Instant editedAt) {
        LambdaUpdateWrapper<PostEntity> wrapper = Wrappers.<PostEntity>lambdaUpdate()
                .set(PostEntity::getContent, content)
                .set(PostEntity::getTopicId, topicId)
                .set(PostEntity::getMediaType, mediaType)
                .setSql("version = version + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getAuthorId, authorId)
                .eq(PostEntity::getVersion, version)
                .ne(PostEntity::getStatus, PostStatus.DELETED)
                .isNull(PostEntity::getDeletedAt);
        if (editedAt != null) {
            wrapper.set(PostEntity::getEditedAt, editedAt);
        }
        return mapper.update(null, wrapper);
    }

    public int publishDraft(String id, String authorId, Instant publishedAt) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .set(PostEntity::getStatus, PostStatus.PUBLISHED)
                .set(PostEntity::getPublishedAt, publishedAt)
                .set(PostEntity::getScheduledAt, null)
                .setSql("version = version + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getAuthorId, authorId)
                .eq(PostEntity::getStatus, PostStatus.DRAFT)
                .isNull(PostEntity::getDeletedAt));
    }

    public int schedule(String id, String authorId, Instant scheduledAt) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .set(PostEntity::getStatus, PostStatus.SCHEDULED)
                .set(PostEntity::getScheduledAt, scheduledAt)
                .setSql("version = version + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getAuthorId, authorId)
                .in(PostEntity::getStatus, PostStatus.DRAFT, PostStatus.SCHEDULED)
                .isNull(PostEntity::getDeletedAt));
    }

    public int cancelSchedule(String id, String authorId) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .set(PostEntity::getStatus, PostStatus.DRAFT)
                .set(PostEntity::getScheduledAt, null)
                .setSql("version = version + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getAuthorId, authorId)
                .eq(PostEntity::getStatus, PostStatus.SCHEDULED)
                .isNull(PostEntity::getDeletedAt));
    }

    /**
     * 定时任务条件发布，重复扫描不会重复更新。
     */
    public int publishScheduled(String id, Instant publishedAt) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .set(PostEntity::getStatus, PostStatus.PUBLISHED)
                .set(PostEntity::getPublishedAt, publishedAt)
                .setSql("version = version + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getStatus, PostStatus.SCHEDULED)
                .isNull(PostEntity::getDeletedAt));
    }

    public int softDelete(String id, String authorId, Instant deletedAt) {
        return mapper.update(null, Wrappers.<PostEntity>lambdaUpdate()
                .set(PostEntity::getStatus, PostStatus.DELETED)
                .set(PostEntity::getDeletedAt, deletedAt)
                .setSql("version = version + 1")
                .eq(PostEntity::getId, id)
                .eq(PostEntity::getAuthorId, authorId)
                .ne(PostEntity::getStatus, PostStatus.DELETED)
                .isNull(PostEntity::getDeletedAt));
    }

    public List<PostEntity> findScheduledDue(Instant now, int limit) {
        return mapper.selectList(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getStatus, PostStatus.SCHEDULED)
                .le(PostEntity::getScheduledAt, now)
                .isNull(PostEntity::getDeletedAt)
                .orderByAsc(PostEntity::getScheduledAt)
                .last("LIMIT " + limit));
    }

    public List<PostEntity> findStaleDrafts(Instant before, int limit) {
        return mapper.selectList(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getStatus, PostStatus.DRAFT)
                .lt(PostEntity::getUpdatedAt, before)
                .isNull(PostEntity::getDeletedAt)
                .orderByAsc(PostEntity::getUpdatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 已软删除且超过保留期的动态，供清理任务物理删除。
     *
     * @param before 截止时间
     * @param limit  单批条数
     * @return 动态列表
     */
    public List<PostEntity> findPurgeable(Instant before, int limit) {
        return mapper.selectList(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getStatus, PostStatus.DELETED)
                .isNotNull(PostEntity::getDeletedAt)
                .le(PostEntity::getDeletedAt, before)
                .orderByAsc(PostEntity::getDeletedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 物理删除。
     *
     * @param id 动态 ID
     */
    public void hardDelete(String id) {
        mapper.deleteById(id);
    }

    public long countDrafts(String authorId) {
        Long count = mapper.selectCount(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getAuthorId, authorId)
                .eq(PostEntity::getStatus, PostStatus.DRAFT)
                .isNull(PostEntity::getDeletedAt));
        return count == null ? 0 : count;
    }

    /**
     * 我的发布列表，使用游标分页。
     *
     * @param status     可选状态；为空时按更新时间排序
     * @param cursorTime 游标时间
     * @param cursorId   游标 ID
     * @param limit      最大条数
     */
    /**
     * 最新流：按发布时间倒序，使用发布时间的复合游标。
     *
     * @param cursorTime 游标时间
     * @param cursorId   游标 ID
     * @param limit      最大条数
     * @return 公开动态
     */
    public List<PostEntity> findLatest(Instant cursorTime, String cursorId, int limit) {
        LambdaQueryWrapper<PostEntity> wrapper = Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt)
                .isNotNull(PostEntity::getPublishedAt);
        appendPublishedCursor(wrapper, cursorTime, cursorId);
        return mapper.selectList(wrapper
                .orderByDesc(PostEntity::getPublishedAt)
                .orderByDesc(PostEntity::getId)
                .last("LIMIT " + limit));
    }

    /**
     * 关注流：限定作者集合，按发布时间倒序。
     *
     * @param authorIds       作者 ID 集合
     * @param excludeAuthorId 需要排除的作者，通常为当前用户
     * @param cursorTime      游标时间
     * @param cursorId        游标 ID
     * @param limit           最大条数
     * @return 公开动态
     */
    public List<PostEntity> findByAuthors(Collection<String> authorIds, String excludeAuthorId,
                                          Instant cursorTime, String cursorId, int limit) {
        if (authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PostEntity> wrapper = Wrappers.<PostEntity>lambdaQuery()
                .in(PostEntity::getAuthorId, authorIds)
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt)
                .isNotNull(PostEntity::getPublishedAt);
        if (excludeAuthorId != null) {
            wrapper.ne(PostEntity::getAuthorId, excludeAuthorId);
        }
        appendPublishedCursor(wrapper, cursorTime, cursorId);
        return mapper.selectList(wrapper
                .orderByDesc(PostEntity::getPublishedAt)
                .orderByDesc(PostEntity::getId)
                .last("LIMIT " + limit));
    }

    /**
     * 推荐候选：时间窗口内的公开动态，按发布时间倒序取上限。
     *
     * @param since 起始时间
     * @param limit 最大条数
     * @return 候选动态
     */
    public List<PostEntity> findRecommendedCandidates(Instant since, int limit) {
        return mapper.selectList(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getStatus, PostStatus.PUBLISHED)
                .isNull(PostEntity::getDeletedAt)
                .isNotNull(PostEntity::getPublishedAt)
                .ge(PostEntity::getPublishedAt, since)
                .orderByDesc(PostEntity::getPublishedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 统计作者在指定时间之后发布的动态数。
     *
     * @param authorIds 作者 ID 集合
     * @param since     起始时间
     * @return 作者 ID 到发布数的映射
     */
    public Map<String, Long> countPublishedByAuthorsSince(Collection<String> authorIds, Instant since) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<PostEntity> wrapper = new QueryWrapper<>();
        wrapper.select("author_id", "COUNT(*) AS cnt")
                .in("author_id", authorIds)
                .eq("status", PostStatus.PUBLISHED)
                .isNull("deleted_at")
                .ge("published_at", since)
                .groupBy("author_id");
        return mapper.selectMaps(wrapper).stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("author_id")),
                row -> row.get("cnt") instanceof Number number ? number.longValue() : 0L));
    }

    /**
     * 追加发布时间复合游标条件，游标字段必须与排序字段一致。
     */
    private void appendPublishedCursor(LambdaQueryWrapper<PostEntity> wrapper,
                                       Instant cursorTime, String cursorId) {
        if (cursorTime == null) {
            return;
        }
        wrapper.and(w -> w.lt(PostEntity::getPublishedAt, cursorTime)
                .or(n -> n.eq(PostEntity::getPublishedAt, cursorTime)
                        .lt(PostEntity::getId, cursorId)));
    }

    public List<PostEntity> findMine(String authorId, String status, Instant cursorTime,
                                     String cursorId, int limit) {
        boolean published = PostStatus.PUBLISHED.equals(status);
        LambdaQueryWrapper<PostEntity> wrapper = Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getAuthorId, authorId)
                .isNull(PostEntity::getDeletedAt);
        if (status != null) {
            wrapper.eq(PostEntity::getStatus, status);
        }
        if (published) {
            if (cursorTime != null) {
                wrapper.and(w -> w.lt(PostEntity::getPublishedAt, cursorTime)
                        .or(n -> n.eq(PostEntity::getPublishedAt, cursorTime)
                                .lt(PostEntity::getId, cursorId)));
            }
            wrapper.orderByDesc(PostEntity::getPublishedAt).orderByDesc(PostEntity::getId);
        } else {
            if (cursorTime != null) {
                wrapper.and(w -> w.lt(PostEntity::getUpdatedAt, cursorTime)
                        .or(n -> n.eq(PostEntity::getUpdatedAt, cursorTime)
                                .lt(PostEntity::getId, cursorId)));
            }
            wrapper.orderByDesc(PostEntity::getUpdatedAt).orderByDesc(PostEntity::getId);
        }
        return mapper.selectList(wrapper.last("LIMIT " + limit));
    }
}
