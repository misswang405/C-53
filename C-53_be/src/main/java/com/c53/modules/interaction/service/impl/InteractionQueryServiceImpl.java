package com.c53.modules.interaction.service.impl;

import com.c53.modules.interaction.repository.InteractionRepository;
import com.c53.modules.interaction.service.InteractionQueryService;
import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.service.PostInteractionService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 互动摘要查询实现，结合动态计数和当前用户关系状态组装结果。
 *
 * <p>属于 interaction 模块的 service.impl 分层，实现 {@link InteractionQueryService}。
 * 职责是为一批动态 ID 批量组装互动摘要（点赞数、评论数、当前访问者是否已点赞、是否已收藏），
 * 供动态列表、动态详情等展示场景复用，调用方无需再逐个查询互动关系。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>只返回「存在且已发布」的动态，草稿、定时未发布与已删除的动态不会出现在结果中，
 *       避免摘要接口泄露不可见动态的存在性。</li>
 *   <li>动态实体、点赞关系、收藏关系各做一次批量查询后在内存中组装，避免按动态逐条查询（N+1）。</li>
 *   <li>计数空值按 0 处理、异常负数截断为 0，保证对外展示的计数始终非负。</li>
 * </ul>
 */
@Service
public class InteractionQueryServiceImpl implements InteractionQueryService {

    /**
     * 互动关系仓储，负责按访问者批量查询其与动态的点赞、收藏关系。
     *
     * <p>本类只使用 {@link InteractionRepository#findLiked} 与
     * {@link InteractionRepository#findBookmarked}；当访问者 ID 为空（匿名访问）时，
     * 仓储会直接返回空 Map，因此摘要中的关系状态会回落为 {@code false}。
     */
    private final InteractionRepository repository;

    /**
     * 动态「写侧」服务，此处借用其已发布动态的批量读取能力。
     *
     * <p>通过 {@link PostInteractionService#findPublished(Collection)} 获取状态为已发布、
     * 发布时间非空且未软删除的动态实体，并从中读取点赞数与评论数。
     * 与 {@link #repository} 的分工是：前者提供动态本身及其计数，后者只提供访问者的互动关系。</p>
     */
    private final PostInteractionService postService;

    /**
     * 构造方法，两个依赖均由 Spring 以构造器方式注入。
     *
     * <p>字段声明为 {@code final}，注入后引用不可变；本类不持有可变状态，可安全共享。
     * 构造器注入也便于单元测试直接传入替身实现。</p>
     *
     * @param repository  互动关系仓储
     * @param postService 已发布动态实体读取服务
     */
    public InteractionQueryServiceImpl(InteractionRepository repository,
                                       PostInteractionService postService) {
        this.repository = repository;
        this.postService = postService;
    }

    /**
     * 批量查询动态互动摘要。
     *
     * <p>根据一批动态 ID 与当前访问者 ID，批量返回点赞数、评论数、是否已点赞、是否已收藏。
     * 实现上先分别批量查询已发布动态、访问者的点赞关系、访问者的收藏关系（共 3 次查询），
     * 再按入参顺序在内存中组装，避免逐条查询造成 N+1。</p>
     *
     * <p>可见性约束：只有状态为已发布、发布时间非空且未软删除的动态才会进入结果；
     * 不存在或不可见的动态被直接跳过，因此返回条目数可能少于入参数量，且结果 Map 为
     * {@link HashMap}，迭代顺序不保证与入参顺序一致。</p>
     *
     * <p>匿名访问：{@code viewerId} 为 {@code null} 时，仓储侧的点赞/收藏查询直接返回空 Map，
     * 摘要中的 {@code likedByMe}/{@code bookmarkedByMe} 经 {@code getOrDefault} 兜底为 {@code false}。</p>
     *
     * @param postIds  动态 ID 集合；为 {@code null} 或空集合时直接返回空 Map，不发起任何查询
     * @param viewerId 当前访问者用户 ID，用于查询其点赞/收藏状态，可为 {@code null}（匿名）
     * @return key 为动态 ID、value 为 {@link InteractionSummary} 的映射，仅包含已发布且存在的动态
     */
    @Override
    public Map<String, InteractionSummary> findSummaries(Collection<String> postIds, String viewerId) {
        // 没有待查询的动态时直接早退：此时任何批量查询都不会产生有效结果，徒增数据库往返。
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        // 三次批量查询：动态（含点赞/评论计数）、访问者点赞关系、访问者收藏关系；
        // 先批量取回再在内存中组装，避免每个动态各查两次关系造成 N+1。
        Map<String, PostEntity> posts = postService.findPublished(postIds);
        Map<String, Boolean> liked = repository.findLiked(viewerId, postIds);
        Map<String, Boolean> bookmarked = repository.findBookmarked(viewerId, postIds);
        Map<String, InteractionSummary> result = new HashMap<>();
        for (String postId : postIds) {
            PostEntity post = posts.get(postId);
            // 查不到说明动态不存在、未发布或已删除，不对外暴露，直接跳过该 ID。
            if (post == null) {
                continue;
            }
            result.put(postId, new InteractionSummary(
                    value(post.getLikeCount()),
                    // 关系表中未命中即视为未点赞/未收藏，匿名访问时同样回落为 false。
                    liked.getOrDefault(postId, false),
                    bookmarked.getOrDefault(postId, false),
                    value(post.getCommentCount())));
        }
        return result;
    }

    /**
     * 把可空的计数字段转换为非负的 {@code long}。
     *
     * <p>调用前提：仅用于动态实体上的点赞数、评论数等计数列。数据库值为 {@code NULL} 时按 0 处理；
     * 出现异常负数时截断为 0，用于兜底脏数据，避免前端展示出负数计数。</p>
     *
     * <p>该方法不抛异常，始终返回非负值。</p>
     *
     * @param count 原始计数，可为 {@code null}
     * @return 非负计数；{@code null} 返回 0
     */
    private static long value(Long count) {
        // 计数异常时保证展示不小于 0：null 视为 0，负数截断为 0。
        return count == null ? 0 : Math.max(0, count);
    }
}
