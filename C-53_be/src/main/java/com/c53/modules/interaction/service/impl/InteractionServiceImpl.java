package com.c53.modules.interaction.service.impl;

import com.c53.common.ApiException;
import com.c53.common.ratelimit.RateLimitResult;
import com.c53.common.ratelimit.RedisRateLimiter;
import com.c53.common.support.IdGenerator;
import com.c53.config.InteractionProperties;
import com.c53.modules.auth.service.AuthSessionGuard;
import com.c53.modules.auth.service.UserSummaryService;
import com.c53.modules.auth.service.UserSummaryService.UserSummary;
import com.c53.modules.interaction.dto.InteractionDtos.BookmarkListResponse;
import com.c53.modules.interaction.dto.InteractionDtos.BookmarkResponse;
import com.c53.modules.interaction.dto.InteractionDtos.CommentListResponse;
import com.c53.modules.interaction.dto.InteractionDtos.CommentRequest;
import com.c53.modules.interaction.dto.InteractionDtos.CommentView;
import com.c53.modules.interaction.dto.InteractionDtos.LikeResponse;
import com.c53.modules.interaction.dto.InteractionDtos.ShareEventRequest;
import com.c53.modules.interaction.entity.CommentEntity;
import com.c53.modules.interaction.entity.PostBookmarkEntity;
import com.c53.modules.interaction.entity.PostLikeEntity;
import com.c53.modules.interaction.entity.ShareEventEntity;
import com.c53.modules.interaction.repository.InteractionRepository;
import com.c53.modules.interaction.service.InteractionService;
import com.c53.modules.interaction.support.InteractionRules;
import com.c53.modules.post.dto.PostDtos.PostResponse;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.service.PostInteractionService;
import com.c53.modules.post.service.PostService;
import com.c53.modules.post.support.CursorCodec;
import com.c53.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 互动领域服务实现，负责权限、限流、幂等写入和分页读取。
 *
 * <p>位于互动模块的 Service 层，实现 {@link InteractionService}，向上为
 * {@code InteractionController} 提供点赞、取消点赞、收藏、取消收藏、评论与回复、
 * 分享事件记录以及收藏列表查询能力；向下依赖 {@link InteractionRepository}
 * 持久化互动关系，依赖 {@link PostInteractionService} 维护动态计数，
 * 依赖 {@link PostService} 批量读取动态展示视图。</p>
 *
 * <p>整体设计：</p>
 * <ul>
 *   <li><b>权限校验</b>：写操作统一先经 {@link #requireActive(AuthPrincipal)} 校验登录态，
 *       除 JWT 签名外还会通过 {@link AuthSessionGuard} 确认会话未被撤销、账号仍为 ACTIVE；
 *       评论读取允许匿名访问。</li>
 *   <li><b>限流</b>：每个接口在真正访问数据库前调用 {@link #enforce(String, int, String)}
 *       做固定窗口限流，写操作为「用户维度 Key」，评论读取为「IP 维度 Key」，
 *       阈值来自 {@link InteractionProperties.Limits}。</li>
 *   <li><b>幂等写入</b>：点赞、收藏依赖数据库唯一关系，按受影响行数决定是否增减动态计数，
 *       重复请求不会造成计数漂移。</li>
 *   <li><b>分页读取</b>：评论与收藏列表使用「时间 + ID」复合游标分页，查询时多取一条判断
 *       {@code hasMore}，避免深分页以及同一时间戳记录的重复或遗漏。</li>
 * </ul>
 */
@Service
public class InteractionServiceImpl implements InteractionService {

    /** 互动关系仓储：点赞、收藏、评论、分享记录的读写入口。 */
    private final InteractionRepository repository;

    /**
     * 动态「写侧」计数服务。
     *
     * <p>只负责点赞/评论计数的增减，以及按 ID 校验动态是否已发布；
     * 与 {@link #postViewService} 职责分离，使写路径不必依赖展示模型的组装逻辑。</p>
     */
    private final PostInteractionService postService;

    /**
     * 动态「读侧」服务。
     *
     * <p>用于批量读取已发布动态的展示视图（{@link PostResponse}），目前服务于收藏列表：
     * 收藏记录只保存动态 ID，展示数据需回查并按可见性过滤。它与 {@link #postService}
     * 的区别在于面向展示、支持批量查询，而前者面向计数与存在性判断。</p>
     */
    private final PostService postViewService;

    /** 用户摘要服务，评论视图需要作者昵称、头像等概要信息，支持按 ID 批量查询。 */
    private final UserSummaryService userSummaryService;

    /** 会话守卫，校验「用户 ID + 会话 ID」对应的会话仍然有效且账号为 ACTIVE。 */
    private final AuthSessionGuard guard;

    /** Redis 固定窗口限流器，按 Key 统计窗口内的请求次数。 */
    private final RedisRateLimiter rateLimiter;

    /** 互动限流配置（{@code interaction.limits.*}），提供各接口每分钟的请求上限。 */
    private final InteractionProperties properties;

    /** 编程式事务模板，用于把「互动关系写入」与「动态计数更新」收敛到同一事务边界内。 */
    private final TransactionTemplate transactionTemplate;

    /** 统一时钟，替代直接调用 {@code Instant.now()}，便于测试中固定时间。 */
    private final Clock clock;

    /**
     * 构造方法，全部依赖由 Spring 以构造器方式注入。
     *
     * <p>所有字段均为 {@code final}，实例创建后依赖引用不可变；服务本身不持有可变状态，
     * 可被多个请求线程安全共享。</p>
     *
     * @param repository          互动关系仓储
     * @param postService         动态计数写入服务
     * @param postViewService     动态展示视图读取服务
     * @param userSummaryService  用户摘要服务
     * @param guard               会话有效性守卫
     * @param rateLimiter         Redis 限流器
     * @param properties          互动限流配置
     * @param transactionTemplate 编程式事务模板
     * @param clock               统一时钟
     */
    public InteractionServiceImpl(InteractionRepository repository,
                                  PostInteractionService postService,
                                  PostService postViewService,
                                  UserSummaryService userSummaryService,
                                  AuthSessionGuard guard,
                                  RedisRateLimiter rateLimiter,
                                  InteractionProperties properties,
                                  TransactionTemplate transactionTemplate,
                                  Clock clock) {
        this.repository = repository;
        this.postService = postService;
        this.postViewService = postViewService;
        this.userSummaryService = userSummaryService;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * 点赞动态。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（{@code interaction:like:{userId}}，
     * 阈值 {@code likePerMinute}，默认 120 次/分钟）→ 动态已发布校验 → 写入点赞关系。
     * 关系写入与计数累加在同一事务内完成。</p>
     *
     * <p>幂等性：插入采用唯一键忽略语义，只有受影响行数大于 0（即真正新增）时才累加
     * 动态点赞数，重复点赞或并发重复提交不会让计数漂移。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @return {@link LikeResponse}：{@code active=true} 表示已点赞，{@code count} 为操作后的最新点赞总数
     * @throws ApiException 401 未登录或会话失效；404 动态不存在或不可见；
     *                      429 触发点赞频率限制（错误码 {@code RATE_LIMITED}，含 {@code retryAfter} 明细）
     */
    @Override
    public LikeResponse like(AuthPrincipal principal, String postId) {
        requireActive(principal);
        enforce("interaction:like:" + principal.userId(), properties.getLimits().getLikePerMinute(),
                "点赞操作过于频繁，请稍后再试");
        requirePublished(postId);
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            PostLikeEntity entity = new PostLikeEntity();
            entity.setId(IdGenerator.newId("lik"));
            entity.setPostId(postId);
            entity.setUserId(principal.userId());
            entity.setCreatedAt(now);
            // 唯一关系真正新增时才累加计数：受影响行数为 0 说明关系已存在（重复或并发点赞），
            // 跳过计数更新以保证幂等，避免点赞数被重复抬高。
            if (repository.insertLikeIgnore(entity) > 0) {
                postService.incrementLike(postId);
            }
        });
        return new LikeResponse(true, currentLikeCount(postId));
    }

    /**
     * 取消点赞。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（与点赞共用 {@code interaction:like:{userId}} 计数）
     * → 动态已发布校验 → 删除点赞关系。</p>
     *
     * <p>幂等性：仅当删除确有命中（受影响行数大于 0）时才扣减计数，
     * 重复取消不会把点赞数扣成负数或产生额外漂移。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @return {@link LikeResponse}：{@code active=false} 表示未点赞，{@code count} 为操作后的最新点赞总数
     * @throws ApiException 401 未登录或会话失效；404 动态不存在或不可见；429 触发频率限制
     */
    @Override
    public LikeResponse unlike(AuthPrincipal principal, String postId) {
        requireActive(principal);
        enforce("interaction:like:" + principal.userId(), properties.getLimits().getLikePerMinute(),
                "点赞操作过于频繁，请稍后再试");
        requirePublished(postId);
        transactionTemplate.executeWithoutResult(status -> {
            // 仅在删除到点赞记录时扣减，重复取消保持幂等。
            if (repository.deleteLike(postId, principal.userId()) > 0) {
                postService.decrementLike(postId);
            }
        });
        return new LikeResponse(false, currentLikeCount(postId));
    }

    /**
     * 收藏动态。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（{@code interaction:bookmark:{userId}}，
     * 阈值 {@code bookmarkPerMinute}，默认 120 次/分钟）→ 动态已发布校验 → 写入收藏关系。
     * 收藏不改变动态计数，只维护关系表，因此无需事务包裹。</p>
     *
     * <p>幂等性：写入依赖唯一键忽略语义，重复收藏不会产生第二条记录，也不会刷新首次收藏时间。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @return {@link BookmarkResponse}：{@code active=true} 表示已收藏
     * @throws ApiException 401 未登录或会话失效；404 动态不存在或不可见；429 触发频率限制
     */
    @Override
    public BookmarkResponse bookmark(AuthPrincipal principal, String postId) {
        requireActive(principal);
        enforce("interaction:bookmark:" + principal.userId(), properties.getLimits().getBookmarkPerMinute(),
                "收藏操作过于频繁，请稍后再试");
        requirePublished(postId);
        PostBookmarkEntity entity = new PostBookmarkEntity();
        entity.setId(IdGenerator.newId("bmk"));
        entity.setPostId(postId);
        entity.setUserId(principal.userId());
        entity.setBookmarkedAt(clock.instant());
        // 唯一键冲突时忽略：已收藏视为成功，保持接口幂等，且不覆盖原始收藏时间（影响收藏列表排序）。
        repository.insertBookmarkIgnore(entity);
        return new BookmarkResponse(true);
    }

    /**
     * 取消收藏。
     *
     * <p>执行顺序：登录态校验 → 与收藏共用限流 Key（{@code interaction:bookmark:{userId}}）
     * → 动态已发布校验 → 删除收藏关系。删除不到记录时同样返回成功，保证幂等。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @return {@link BookmarkResponse}：{@code active=false} 表示未收藏
     * @throws ApiException 401 未登录或会话失效；404 动态不存在或不可见；429 触发频率限制
     */
    @Override
    public BookmarkResponse unbookmark(AuthPrincipal principal, String postId) {
        requireActive(principal);
        enforce("interaction:bookmark:" + principal.userId(), properties.getLimits().getBookmarkPerMinute(),
                "收藏操作过于频繁，请稍后再试");
        requirePublished(postId);
        repository.deleteBookmark(postId, principal.userId());
        return new BookmarkResponse(false);
    }

    /**
     * 分页读取动态的一级评论及其回复。
     *
     * <p>该接口允许匿名访问，因此不做登录态校验，但按客户端 IP 限流：
     * Key 为 {@code interaction:comments:ip:{clientIp}}，阈值 {@code commentsReadPerMinute}
     * （默认 120 次/分钟）；IP 解析失败时以 {@code unknown} 兜底，避免限流失效。</p>
     *
     * <p>分页策略：按 {@code limit + 1} 查询根评论，结果多于 {@code limit} 说明还有下一页，
     * 截断后以本页最后一条评论的「创建时间 + ID」编码为 {@code nextCursor}；
     * 若没有下一页则返回 {@code null}。回复与作者信息均为批量加载，避免逐条查询造成 N+1。</p>
     *
     * @param principal 当前用户，可为 {@code null}（匿名可读）
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @param cursor    上一页返回的游标，为空表示从第一页开始
     * @param limit     每页条数，非正数按 20 处理，超过 50 时按 50 处理
     * @param clientIp  客户端 IP，仅用于限流 Key
     * @return {@link CommentListResponse}：{@code items} 为本页评论，
     *         {@code nextCursor} 为 {@code null} 表示没有下一页
     * @throws ApiException 400 游标格式非法（{@code VALIDATION_ERROR}）；404 动态不存在或不可见；
     *                      429 触发评论读取频率限制
     */
    @Override
    public CommentListResponse comments(AuthPrincipal principal, String postId, String cursor,
                                        int limit, String clientIp) {
        // 匿名可读，因此以 IP 作为限流维度，防止单机高频抓取评论。
        enforce("interaction:comments:ip:" + (clientIp == null ? "unknown" : clientIp),
                properties.getLimits().getCommentsReadPerMinute(), "评论读取过于频繁，请稍后再试");
        requirePublished(postId);
        int size = normalizeLimit(limit);
        CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
        // 多取一条判断下一页；回复和作者批量加载，避免逐条查询。
        List<CommentEntity> roots = repository.findRoots(postId,
                decoded == null ? null : decoded.time(), decoded == null ? null : decoded.id(), size + 1);
        boolean hasMore = roots.size() > size;
        List<CommentEntity> page = hasMore ? roots.subList(0, size) : roots;
        List<CommentEntity> replies = repository.findReplies(page.stream().map(CommentEntity::getId).toList());
        Map<String, List<CommentEntity>> grouped = replies.stream().collect(Collectors.groupingBy(
                CommentEntity::getParentId, HashMap::new, Collectors.toList()));
        Map<String, UserSummary> users = loadUsers(page, replies);
        List<CommentView> items = page.stream()
                .map(root -> toView(root, grouped.getOrDefault(root.getId(), List.of()), users))
                .toList();
        // 游标取本页最后一条而非「多取的一条」，保证下一页从本页末尾之后继续，不会跳过数据。
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            CommentEntity last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CommentListResponse(items, nextCursor);
    }

    /**
     * 创建一级评论。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（{@code interaction:comment:{userId}}，
     * 阈值 {@code commentPerMinute}，默认 30 次/分钟）→ 动态已发布校验 → 委托给私有的
     * {@link #createComment(String, String, String, CommentRequest)} 完成内容规范化与事务写入。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @param request   评论请求体，{@code content} 不能为空
     * @return 新建评论视图，回复列表为空
     * @throws ApiException 401 未登录或会话失效；400 评论内容为空、超长或含非法字符；
     *                      404 动态不存在或不可见；429 触发评论频率限制
     */
    @Override
    public CommentView createComment(AuthPrincipal principal, String postId, CommentRequest request) {
        requireActive(principal);
        enforce("interaction:comment:" + principal.userId(), properties.getLimits().getCommentPerMinute(),
                "评论操作过于频繁，请稍后再试");
        requirePublished(postId);
        return createComment(principal.userId(), postId, null, request);
    }

    /**
     * 回复一级评论，创建二级评论。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（与发表评论共用
     * {@code interaction:comment:{userId}}）→ 父评论存在且可见校验 → 动态已发布校验
     * → 委托私有 {@link #createComment(String, String, String, CommentRequest)} 写入。</p>
     *
     * <p>结构约束：只允许回复一级评论，父评论本身是回复时直接拒绝，以维持「评论 - 回复」
     * 两层结构，避免出现任意深度的楼中楼。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param commentId 被回复的一级评论 ID
     * @param request   评论请求体，{@code content} 不能为空
     * @return 新建回复视图，回复列表为空
     * @throws ApiException 401 未登录或会话失效；404 父评论不存在或已删除、动态不存在；
     *                      400 父评论本身是回复（{@code INVALID_COMMENT_PARENT}）或内容不合法；
     *                      429 触发评论频率限制
     */
    @Override
    public CommentView createReply(AuthPrincipal principal, String commentId, CommentRequest request) {
        requireActive(principal);
        enforce("interaction:comment:" + principal.userId(), properties.getLimits().getCommentPerMinute(),
                "评论操作过于频繁，请稍后再试");
        CommentEntity parent = repository.findComment(commentId)
                .filter(comment -> InteractionRepository.VISIBLE.equals(comment.getStatus()))
                .filter(comment -> comment.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在"));
        // 回复只能挂在一级评论下，保持两层评论结构。
        if (parent.getParentId() != null) {
            throw ApiException.badRequest("INVALID_COMMENT_PARENT", "只能回复一级评论");
        }
        requirePublished(parent.getPostId());
        return createComment(principal.userId(), parent.getPostId(), parent.getId(), request);
    }

    /**
     * 记录分享事件。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（{@code interaction:share:{userId}}，
     * 阈值 {@code sharePerMinute}，默认 60 次/分钟）→ 动态已发布校验 → 追加一条分享流水。
     * 分享仅做埋点记录，不改变动态计数，也不返回内容。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param postId    动态 ID，必须是已发布且未删除的动态
     * @param request   分享请求体，{@code channel} 必须是已开放渠道
     *                  （{@code WECHAT}/{@code QQ}/{@code DOUYIN}，大小写不敏感）
     * @throws ApiException 401 未登录或会话失效；404 动态不存在或不可见；
     *                      400 分享渠道不支持（{@code INVALID_SHARE_CHANNEL}）；429 触发频率限制
     */
    @Override
    public void share(AuthPrincipal principal, String postId, ShareEventRequest request) {
        requireActive(principal);
        enforce("interaction:share:" + principal.userId(), properties.getLimits().getSharePerMinute(),
                "分享操作过于频繁，请稍后再试");
        requirePublished(postId);
        ShareEventEntity entity = new ShareEventEntity();
        entity.setId(IdGenerator.newId("shr"));
        entity.setPostId(postId);
        entity.setUserId(principal.userId());
        // 渠道先归一化（去空白并转大写）再按白名单校验，非法值抛 400，避免脏数据写入。
        entity.setChannel(InteractionRules.normalizeChannel(request == null ? null : request.channel()));
        entity.setCreatedAt(clock.instant());
        repository.insertShareEvent(entity);
    }

    /**
     * 分页读取当前用户收藏的动态。
     *
     * <p>执行顺序：登录态校验 → 按用户限流（{@code interaction:bookmarks:{userId}}，
     * 阈值 {@code bookmarksReadPerMinute}，默认 60 次/分钟）→ 游标分页查询收藏记录。
     * 分页同样采用「多取一条」判断 {@code hasMore}。</p>
     *
     * <p>组装规则：排序与游标都以收藏记录为准（收藏时间 + 动态 ID），随后批量读取动态展示视图，
     * 已删除或在当前用户视角下不可见的动态会在组装阶段被过滤掉，
     * 因此返回条数可能少于 {@code limit}，但不影响游标推进。</p>
     *
     * @param principal 当前登录用户，不能为空
     * @param cursor    上一页返回的游标，为空表示从第一页开始
     * @param limit     每页条数，非正数按 20 处理，超过 50 时按 50 处理
     * @return {@link BookmarkListResponse}：{@code items} 为本页可见的动态视图，
     *         {@code nextCursor} 为 {@code null} 表示没有下一页
     * @throws ApiException 401 未登录或会话失效；400 游标格式非法；429 触发收藏读取频率限制
     */
    @Override
    public BookmarkListResponse bookmarks(AuthPrincipal principal, String cursor, int limit) {
        requireActive(principal);
        enforce("interaction:bookmarks:" + principal.userId(),
                properties.getLimits().getBookmarksReadPerMinute(), "收藏读取过于频繁，请稍后再试");
        int size = normalizeLimit(limit);
        CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
        List<com.c53.modules.interaction.entity.PostBookmarkEntity> rows = repository.findBookmarks(
                principal.userId(), decoded == null ? null : decoded.time(),
                decoded == null ? null : decoded.id(), size + 1);
        boolean hasMore = rows.size() > size;
        List<com.c53.modules.interaction.entity.PostBookmarkEntity> page = hasMore
                ? rows.subList(0, size) : rows;
        // 收藏记录决定顺序和游标，已删除或不可见动态在组装展示结果时过滤。
        // 一次批量查询动态视图，避免按收藏记录逐条查询（N+1）。
        Map<String, PostResponse> views = postViewService.getPublishedViews(
                        principal, page.stream().map(com.c53.modules.interaction.entity.PostBookmarkEntity::getPostId).toList())
                .stream().collect(Collectors.toMap(PostResponse::id, view -> view, (a, b) -> a));
        List<PostResponse> items = page.stream()
                .map(row -> views.get(row.getPostId()))
                // 视图中不存在的动态（已删除、转为草稿或无权限）直接丢弃，不暴露给调用方。
                .filter(java.util.Objects::nonNull)
                .toList();
        // 游标仍以最后一条收藏记录推进，即使该动态被过滤，下一页也不会重复读取。
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            var last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getBookmarkedAt(), last.getPostId());
        }
        return new BookmarkListResponse(items, nextCursor);
    }

    /**
     * 一级评论与回复共用的写入逻辑。
     *
     * <p>调用前提：调用方已完成登录校验、限流与动态可见性校验（回复场景还需校验父评论）。
     * 内容先经 {@link InteractionRules#normalizeComment} 规范化：统一换行、去除首尾空白、
     * 拒绝非法控制字符、按 Unicode 码点限制 500 字符。</p>
     *
     * <p>事务边界：评论插入与动态评论计数自增在同一事务内完成。若动态在并发中被删除或
     * 转为不可见，计数自增会返回 0，此时抛出的异常导致整个事务回滚，
     * 避免产生「有评论但动态计数未更新」的孤儿数据。</p>
     *
     * @param userId   评论作者 ID
     * @param postId   动态 ID
     * @param parentId 父评论 ID，一级评论传 {@code null}
     * @param request  评论请求体
     * @return 新建评论视图，回复列表为空
     * @throws ApiException 400 内容为空、超长或含非法控制字符；404 动态不存在或不可见
     */
    private CommentView createComment(String userId, String postId, String parentId,
                                      CommentRequest request) {
        String content = InteractionRules.normalizeComment(request == null ? null : request.content());
        Instant now = clock.instant();
        CommentEntity entity = new CommentEntity();
        entity.setId(IdGenerator.newId("cmt"));
        entity.setPostId(postId);
        entity.setAuthorId(userId);
        entity.setParentId(parentId);
        entity.setContent(content);
        entity.setStatus(InteractionRepository.VISIBLE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        transactionTemplate.executeWithoutResult(status -> {
            // 评论写入和动态计数在同一事务内完成，动态不可用时整体回滚。
            repository.insertComment(entity);
            if (postService.incrementComment(postId) == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在");
            }
        });
        UserSummary author = userSummaryService.findByIds(Set.of(userId)).get(userId);
        return new CommentView(entity.getId(), author, entity.getContent(), entity.getCreatedAt(), List.of(), false);
    }

    /**
     * 批量加载评论作者摘要。
     *
     * <p>把当前页一级评论与回复的作者 ID 去重后一次性查询，
     * 避免按评论逐条查询作者信息（N+1 查询）。</p>
     *
     * @param roots   当前页一级评论
     * @param replies 当前页评论对应的全部回复
     * @return 作者 ID 到用户摘要的映射
     */
    private Map<String, UserSummary> loadUsers(List<CommentEntity> roots, List<CommentEntity> replies) {
        Set<String> ids = java.util.stream.Stream.concat(roots.stream(), replies.stream())
                .map(CommentEntity::getAuthorId).collect(Collectors.toSet());
        return userSummaryService.findByIds(ids);
    }

    /**
     * 把评论实体转换为对外视图，并挂载其回复。
     *
     * <p>单条一级评论最多返回 {@link InteractionRules#MAX_REPLIES_PER_COMMENT}（20）条回复；
     * 超出部分不返回，但通过 {@code truncated=true} 明确告知前端发生了截断，
     * 避免前端误以为回复已全部展示。</p>
     *
     * @param root    一级评论实体
     * @param replies 该评论已查询到的回复列表
     * @param users   作者摘要映射，用于填充作者信息
     * @return 评论视图
     */
    private CommentView toView(CommentEntity root, List<CommentEntity> replies,
                               Map<String, UserSummary> users) {
        // 响应只展示限定数量的回复，并明确标记是否发生截断。
        boolean truncated = replies.size() > InteractionRules.MAX_REPLIES_PER_COMMENT;
        List<CommentView> replyViews = replies.stream().limit(InteractionRules.MAX_REPLIES_PER_COMMENT)
                .map(reply -> new CommentView(reply.getId(), users.get(reply.getAuthorId()), reply.getContent(),
                        reply.getCreatedAt(), List.of(), false)).toList();
        return new CommentView(root.getId(), users.get(root.getAuthorId()), root.getContent(), root.getCreatedAt(),
                replyViews, truncated);
    }

    /**
     * 校验动态存在且已发布（未删除、非草稿、非定时未到点）。
     *
     * <p>所有互动写入与按动态维度的读取都以此作为准入条件，
     * 保证互动只发生在对外可见的动态上。</p>
     *
     * @param postId 动态 ID
     * @return 已发布的动态实体
     * @throws ApiException 动态不存在或不可见时抛出 404（{@code POST_NOT_FOUND}）
     */
    private PostEntity requirePublished(String postId) {
        return postService.findPublished(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在"));
    }

    /**
     * 读取动态的最新点赞数。
     *
     * <p>点赞/取消点赞返回结果时重新查询动态，保证返回值与库中计数一致；
     * 计数为 {@code null} 时按 0 处理。</p>
     *
     * @param postId 动态 ID
     * @return 最新点赞总数
     * @throws ApiException 动态在读取时已不可见时抛出 404
     */
    private long currentLikeCount(String postId) {
        return postService.findPublished(postId).map(post -> post.getLikeCount() == null ? 0 : post.getLikeCount())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "动态不存在"));
    }

    /**
     * 要求当前请求已登录且会话仍然有效。
     *
     * <p>先检查 {@link AuthPrincipal} 是否携带用户 ID 与会话 ID，再调用
     * {@link AuthSessionGuard#requireActive} 校验会话未被撤销、账号状态为 ACTIVE，
     * 防止「签名有效但会话已失效」的令牌继续执行写操作。</p>
     *
     * @param principal 当前用户
     * @throws ApiException 未携带身份信息时抛出 401（{@code UNAUTHORIZED}）；
     *                      会话失效或账号禁用时由会话守卫抛出 401
     */
    private void requireActive(AuthPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.sessionId() == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "请先登录");
        }
        guard.requireActive(principal.userId(), principal.sessionId());
    }

    /**
     * 规范化分页条数。
     *
     * <p>非正数回退为默认值 20；超过 {@link InteractionRules#MAX_COMMENT_LIMIT}（50）时截断，
     * 防止一次拉取过多数据导致慢查询。</p>
     *
     * @param limit 客户端传入的条数
     * @return 实际用于查询的条数
     */
    private int normalizeLimit(int limit) {
        return limit <= 0 ? 20 : Math.min(limit, InteractionRules.MAX_COMMENT_LIMIT);
    }

    /**
     * 执行固定窗口限流，超过阈值直接拒绝。
     *
     * <p>窗口固定为 1 分钟，计数 Key 由调用方按「操作 + 维度（用户或 IP）」拼接。
     * Redis 不可用时 {@link RedisRateLimiter} 按 fail-open 放行，避免限流组件影响主链路。</p>
     *
     * @param key     限流 Key
     * @param limit   窗口内允许的最大次数，取自 {@link InteractionProperties}
     * @param message 触发限流时返回给用户的提示文案
     * @throws ApiException 超过阈值时抛出 429（{@code RATE_LIMITED}），
     *                      {@code details} 中携带 {@code retryAfter} 剩余秒数
     */
    private void enforce(String key, int limit, String message) {
        RateLimitResult result = rateLimiter.tryAcquire(key, limit, Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw ApiException.tooManyRequests("RATE_LIMITED", message,
                    Map.of("retryAfter", result.retryAfterSeconds()));
        }
    }
}
