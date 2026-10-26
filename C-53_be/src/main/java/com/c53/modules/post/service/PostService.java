package com.c53.modules.post.service;

import com.c53.modules.post.dto.PostDtos.CreatePostRequest;
import com.c53.modules.post.dto.PostDtos.PostListResponse;
import com.c53.modules.post.dto.PostDtos.PostResponse;
import com.c53.modules.post.dto.PostDtos.ScheduleRequest;
import com.c53.modules.post.dto.PostDtos.UpdatePostRequest;
import com.c53.security.AuthPrincipal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 动态发布、编辑、定时与删除。
 */
public interface PostService {

    /**
     * 创建结果。
     *
     * @param post    动态视图
     * @param created true 表示新建，false 表示幂等命中已有记录
     */
    record CreateResult(PostResponse post, boolean created) {
    }

    /**
     * 创建草稿、立即发布或定时发布。
     *
     * @param principal 当前用户
     * @param request   创建参数
     * @return 创建结果
     */
    CreateResult create(AuthPrincipal principal, CreatePostRequest request);

    /**
     * 查询动态详情。草稿与定时仅作者可见。
     *
     * @param principal 当前用户，游客为 null
     * @param id        动态 ID
     * @return 动态视图
     */
    PostResponse get(AuthPrincipal principal, String id);

    /**
     * 批量查询公开动态视图，供收藏等列表模块避免逐条查询。
     */
    List<PostResponse> getPublishedViews(AuthPrincipal principal, Collection<String> ids);

    /**
     * 编辑正文、话题与媒体。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @param request   编辑参数
     * @return 更新后的动态
     */
    PostResponse update(AuthPrincipal principal, String id, UpdatePostRequest request);

    /**
     * 立即发布草稿。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 发布后的动态
     */
    PostResponse publish(AuthPrincipal principal, String id);

    /**
     * 设置或修改定时发布。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @param request   定时参数
     * @return 更新后的动态
     */
    PostResponse schedule(AuthPrincipal principal, String id, ScheduleRequest request);

    /**
     * 取消定时，退回草稿。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 更新后的动态
     */
    PostResponse cancelSchedule(AuthPrincipal principal, String id);

    /**
     * 软删除动态。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     */
    void delete(AuthPrincipal principal, String id);

    /**
     * 我的发布列表。
     *
     * @param principal 当前用户
     * @param status    可选状态
     * @param cursor    游标
     * @param limit     条数
     * @return 列表与下一页游标
     */
    PostListResponse myPosts(AuthPrincipal principal, String status, String cursor, int limit);

    /**
     * 首页信息流。
     *
     * @param principal 当前用户，游客为 null
     * @param scope     范围：RECOMMENDED / FOLLOWING / LATEST
     * @param cursor    分页游标
     * @param limit     返回条数
     * @param clientIp  客户端 IP，用于匿名限流
     * @return 动态列表与下一页游标
     */
    PostListResponse feed(AuthPrincipal principal, String scope, String cursor, int limit, String clientIp);

    /**
     * 统计作者在指定时间之后发布的动态数，供推荐创作者打分使用。
     *
     * @param authorIds 作者 ID 集合
     * @param since     起始时间
     * @return 作者 ID 到发布数的映射
     */
    Map<String, Long> countPublishedSince(Collection<String> authorIds, Instant since);
}
