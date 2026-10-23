package com.c53.modules.post.controller;

import com.c53.common.ApiResponse;
import com.c53.modules.post.dto.PostDtos.CreatePostRequest;
import com.c53.modules.post.dto.PostDtos.PostListResponse;
import com.c53.modules.post.dto.PostDtos.PostResponse;
import com.c53.modules.post.dto.PostDtos.ScheduleRequest;
import com.c53.modules.post.dto.PostDtos.UpdatePostRequest;
import com.c53.modules.post.service.PostService;
import com.c53.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态发布接口。
 *
 * <p>写接口必须登录，由 Spring Security 与 Service 层的会话校验双重保护。
 * 幂等命中已有记录时返回 200，新建返回 201。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 创建草稿、立即发布或定时发布。
     *
     * @param principal 当前用户
     * @param request   创建参数
     * @return 动态视图
     */
    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreatePostRequest request) {
        PostService.CreateResult result = postService.create(principal, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(result.post()));
    }

    /**
     * 查询动态详情。草稿与定时仅作者可见。
     *
     * @param principal 当前用户，可为空
     * @param id        动态 ID
     * @return 动态视图
     */
    @GetMapping("/posts/{id}")
    public ApiResponse<PostResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable String id) {
        return ApiResponse.ok(postService.get(principal, id));
    }

    /**
     * 编辑正文、话题与媒体。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @param request   编辑参数
     * @return 更新后的动态
     */
    @PatchMapping("/posts/{id}")
    public ApiResponse<PostResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String id,
                                            @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.ok(postService.update(principal, id, request));
    }

    /**
     * 立即发布草稿。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 发布后的动态
     */
    @PostMapping("/posts/{id}/publish")
    public ApiResponse<PostResponse> publish(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable String id) {
        return ApiResponse.ok(postService.publish(principal, id));
    }

    /**
     * 设置或修改定时发布。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @param request   定时参数
     * @return 更新后的动态
     */
    @PostMapping("/posts/{id}/schedule")
    public ApiResponse<PostResponse> schedule(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String id,
                                              @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(postService.schedule(principal, id, request));
    }

    /**
     * 取消定时，退回草稿。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 更新后的动态
     */
    @PostMapping("/posts/{id}/cancel-schedule")
    public ApiResponse<PostResponse> cancelSchedule(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable String id) {
        return ApiResponse.ok(postService.cancelSchedule(principal, id));
    }

    /**
     * 软删除动态。
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 204
     */
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String id) {
        postService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 我的发布列表。
     *
     * @param principal 当前用户
     * @param status    可选状态
     * @param cursor    游标
     * @param limit     条数
     * @return 列表与下一页游标
     */
    @GetMapping("/me/posts")
    public ApiResponse<PostListResponse> myPosts(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(postService.myPosts(principal, status, cursor, limit));
    }
}
