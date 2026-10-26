package com.c53.modules.interaction.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.modules.interaction.entity.CommentEntity;
import com.c53.modules.interaction.entity.PostBookmarkEntity;
import com.c53.modules.interaction.entity.PostLikeEntity;
import com.c53.modules.interaction.entity.ShareEventEntity;
import com.c53.modules.interaction.mapper.CommentMapper;
import com.c53.modules.interaction.mapper.PostBookmarkMapper;
import com.c53.modules.interaction.mapper.PostLikeMapper;
import com.c53.modules.interaction.mapper.ShareEventMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 互动关系和评论数据访问仓库。
 *
 * <p>负责将多个 Mapper 的查询和聚合操作集中起来，避免服务层直接依赖持久化细节。</p>
 */
@Repository
public class InteractionRepository {

    /** 可展示的评论状态。 */
    public static final String VISIBLE = "VISIBLE";

    private final PostLikeMapper likeMapper;
    private final PostBookmarkMapper bookmarkMapper;
    private final CommentMapper commentMapper;
    private final ShareEventMapper shareEventMapper;

    public InteractionRepository(PostLikeMapper likeMapper, PostBookmarkMapper bookmarkMapper,
                                 CommentMapper commentMapper, ShareEventMapper shareEventMapper) {
        this.likeMapper = likeMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.commentMapper = commentMapper;
        this.shareEventMapper = shareEventMapper;
    }

    /** 幂等写入点赞关系，返回实际新增记录数。 */
    public int insertLikeIgnore(PostLikeEntity entity) {
        return likeMapper.insertIgnore(entity);
    }

    public int deleteLike(String postId, String userId) {
        return likeMapper.delete(Wrappers.<PostLikeEntity>lambdaQuery()
                .eq(PostLikeEntity::getPostId, postId)
                .eq(PostLikeEntity::getUserId, userId));
    }

    public boolean existsLike(String postId, String userId) {
        return likeMapper.selectCount(Wrappers.<PostLikeEntity>lambdaQuery()
                .eq(PostLikeEntity::getPostId, postId)
                .eq(PostLikeEntity::getUserId, userId)) > 0;
    }

    public Map<String, Boolean> findLiked(String userId, Collection<String> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return likeMapper.selectList(Wrappers.<PostLikeEntity>lambdaQuery()
                        .select(PostLikeEntity::getPostId)
                        .eq(PostLikeEntity::getUserId, userId)
                        .in(PostLikeEntity::getPostId, postIds))
                .stream().collect(Collectors.toMap(PostLikeEntity::getPostId, item -> true, (a, b) -> a));
    }

    /** 幂等写入收藏关系，不修改已有收藏的时间。 */
    public int insertBookmarkIgnore(PostBookmarkEntity entity) {
        return bookmarkMapper.insertIgnore(entity);
    }

    public int deleteBookmark(String postId, String userId) {
        return bookmarkMapper.delete(Wrappers.<PostBookmarkEntity>lambdaQuery()
                .eq(PostBookmarkEntity::getPostId, postId)
                .eq(PostBookmarkEntity::getUserId, userId));
    }

    public boolean existsBookmark(String postId, String userId) {
        return bookmarkMapper.selectCount(Wrappers.<PostBookmarkEntity>lambdaQuery()
                .eq(PostBookmarkEntity::getPostId, postId)
                .eq(PostBookmarkEntity::getUserId, userId)) > 0;
    }

    public Map<String, Boolean> findBookmarked(String userId, Collection<String> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return bookmarkMapper.selectList(Wrappers.<PostBookmarkEntity>lambdaQuery()
                        .select(PostBookmarkEntity::getPostId)
                        .eq(PostBookmarkEntity::getUserId, userId)
                        .in(PostBookmarkEntity::getPostId, postIds))
                .stream().collect(Collectors.toMap(PostBookmarkEntity::getPostId, item -> true, (a, b) -> a));
    }

    public void insertComment(CommentEntity entity) {
        commentMapper.insert(entity);
    }

    public Optional<CommentEntity> findComment(String id) {
        return Optional.ofNullable(commentMapper.selectById(id));
    }

    /** 按创建时间和 ID 倒序读取可见一级评论。 */
    public List<CommentEntity> findRoots(String postId, Instant cursorTime, String cursorId, int limit) {
        LambdaQueryWrapper<CommentEntity> wrapper = Wrappers.<CommentEntity>lambdaQuery()
                .eq(CommentEntity::getPostId, postId)
                .isNull(CommentEntity::getParentId)
                .eq(CommentEntity::getStatus, VISIBLE)
                .isNull(CommentEntity::getDeletedAt);
        appendDescCursor(wrapper, cursorTime, cursorId);
        wrapper.orderByDesc(CommentEntity::getCreatedAt, CommentEntity::getId);
        return commentMapper.selectList(wrapper.last("LIMIT " + limit));
    }

    /** 批量读取可见回复，并按创建时间和 ID 正序展示。 */
    public List<CommentEntity> findReplies(Collection<String> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<CommentEntity> wrapper = Wrappers.<CommentEntity>lambdaQuery()
                .in(CommentEntity::getParentId, parentIds)
                .eq(CommentEntity::getStatus, VISIBLE)
                .isNull(CommentEntity::getDeletedAt)
                .orderByAsc(CommentEntity::getCreatedAt, CommentEntity::getId);
        return commentMapper.selectList(wrapper);
    }

    public void insertShareEvent(ShareEventEntity entity) {
        shareEventMapper.insert(entity);
    }

    public Map<String, Long> countLikes(Collection<String> postIds) {
        return countByPost(likeMapper, "post_id", postIds);
    }

    public Map<String, Long> countComments(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<CommentEntity> wrapper = new QueryWrapper<>();
        wrapper.select("post_id", "COUNT(*) AS cnt")
                .in("post_id", postIds)
                .eq("status", VISIBLE)
                .isNull("deleted_at")
                .groupBy("post_id");
        return commentMapper.selectMaps(wrapper).stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("post_id")),
                row -> ((Number) row.get("cnt")).longValue()));
    }

    /** 以收藏时间和动态 ID 组成稳定的倒序分页游标。 */
    public List<PostBookmarkEntity> findBookmarks(String userId, Instant cursorTime, String cursorId, int limit) {
        LambdaQueryWrapper<PostBookmarkEntity> wrapper = Wrappers.<PostBookmarkEntity>lambdaQuery()
                .eq(PostBookmarkEntity::getUserId, userId);
        if (cursorTime != null) {
            wrapper.and(w -> w.lt(PostBookmarkEntity::getBookmarkedAt, cursorTime)
                    .or(n -> n.eq(PostBookmarkEntity::getBookmarkedAt, cursorTime)
                            .lt(PostBookmarkEntity::getPostId, cursorId)));
        }
        wrapper.orderByDesc(PostBookmarkEntity::getBookmarkedAt, PostBookmarkEntity::getPostId);
        return bookmarkMapper.selectList(wrapper.last("LIMIT " + limit));
    }

    /** 同一时间戳下继续比较 ID，避免翻页时漏掉或重复读取评论。 */
    private static void appendDescCursor(LambdaQueryWrapper<CommentEntity> wrapper,
                                         Instant cursorTime, String cursorId) {
        if (cursorTime != null) {
            wrapper.and(w -> w.lt(CommentEntity::getCreatedAt, cursorTime)
                    .or(n -> n.eq(CommentEntity::getCreatedAt, cursorTime)
                            .lt(CommentEntity::getId, cursorId)));
        }
    }

    private static <T> Map<String, Long> countByPost(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                                     String postColumn, Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.select(postColumn, "COUNT(*) AS cnt").in(postColumn, postIds).groupBy(postColumn);
        return mapper.selectMaps(wrapper).stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get(postColumn)),
                row -> ((Number) row.get("cnt")).longValue()));
    }
}
