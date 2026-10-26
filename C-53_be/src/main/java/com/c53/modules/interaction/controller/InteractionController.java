package com.c53.modules.interaction.controller;

import com.c53.common.ApiResponse;
import com.c53.common.ClientIp;
import com.c53.modules.interaction.dto.InteractionDtos.BookmarkListResponse;
import com.c53.modules.interaction.dto.InteractionDtos.BookmarkResponse;
import com.c53.modules.interaction.dto.InteractionDtos.CommentListResponse;
import com.c53.modules.interaction.dto.InteractionDtos.CommentRequest;
import com.c53.modules.interaction.dto.InteractionDtos.CommentView;
import com.c53.modules.interaction.dto.InteractionDtos.LikeResponse;
import com.c53.modules.interaction.dto.InteractionDtos.ShareEventRequest;
import com.c53.modules.interaction.service.InteractionService;
import com.c53.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供点赞、收藏、评论和分享等互动接口。
 */
@RestController
@RequestMapping("/api/v1")
public class InteractionController {

    private final InteractionService service;

    public InteractionController(InteractionService service) {
        this.service = service;
    }

    /**
     * 点赞动态。
     *
     * <p>需登录；按用户维度限流。重复点赞幂等，不会造成计数漂移。</p>
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 点赞状态与最新点赞数
     */
    @PutMapping("/posts/{id}/like")
    public ApiResponse<LikeResponse> like(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable String id) {
        return ApiResponse.ok(service.like(principal, id));
    }

    /**
     * 取消点赞。
     *
     * <p>需登录；按用户维度限流。仅在存在点赞记录时扣减计数，重复取消保持幂等。</p>
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 点赞状态与最新点赞数
     */
    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<LikeResponse> unlike(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String id) {
        return ApiResponse.ok(service.unlike(principal, id));
    }

    /**
     * 收藏动态。
     *
     * <p>需登录；按用户维度限流。重复收藏幂等。</p>
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 收藏状态
     */
    @PutMapping("/posts/{id}/bookmark")
    public ApiResponse<BookmarkResponse> bookmark(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable String id) {
        return ApiResponse.ok(service.bookmark(principal, id));
    }

    /**
     * 取消收藏。
     *
     * <p>需登录；按用户维度限流。</p>
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @return 收藏状态
     */
    @DeleteMapping("/posts/{id}/bookmark")
    public ApiResponse<BookmarkResponse> unbookmark(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable String id) {
        return ApiResponse.ok(service.unbookmark(principal, id));
    }

    /**
     * 分页读取动态评论（含一级回复）。
     *
     * <p>按客户端 IP 限流，可匿名访问；使用游标分页，仅返回已发布动态的可见评论。</p>
     *
     * @param principal 当前用户，可为空
     * @param id        动态 ID
     * @param cursor    下一页游标，首页可为空
     * @param limit     每页条数，默认 20
     * @param request   当前 HTTP 请求，用于解析客户端 IP
     * @return 评论列表与下一页游标
     */
    @GetMapping("/posts/{id}/comments")
    public ApiResponse<CommentListResponse> comments(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable String id,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "20") int limit,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(service.comments(principal, id, cursor, limit, ClientIp.resolve(request)));
    }

    /**
     * 创建一级评论。
     *
     * <p>需登录；按用户维度限流。评论写入与动态计数在同一事务内完成。</p>
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @param request   评论内容
     * @return 201 与新建评论视图
     */
    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<ApiResponse<CommentView>> createComment(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createComment(principal, id, request)));
    }

    /**
     * 回复一级评论，创建二级评论。
     *
     * <p>需登录；按用户维度限流。只能回复一级评论，以维持两层评论结构。</p>
     *
     * @param principal 当前用户
     * @param id        被回复的一级评论 ID
     * @param request   评论内容
     * @return 201 与新建回复视图
     */
    @PostMapping("/comments/{id}/replies")
    public ResponseEntity<ApiResponse<CommentView>> createReply(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createReply(principal, id, request)));
    }

    /**
     * 记录分享事件。
     *
     * <p>需登录；按用户维度限流。仅追加分享流水，不返回内容。</p>
     *
     * @param principal 当前用户
     * @param id        动态 ID
     * @param request   分享渠道等信息
     * @return 204
     */
    @PostMapping("/posts/{id}/share-events")
    public ResponseEntity<Void> share(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String id,
                                      @Valid @RequestBody ShareEventRequest request) {
        service.share(principal, id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 当前用户的收藏列表。
     *
     * <p>需登录；按用户维度限流。以收藏时间游标分页，已删除或不可见的动态在组装结果时过滤。</p>
     *
     * @param principal 当前用户
     * @param cursor    下一页游标，首页可为空
     * @param limit     每页条数，默认 20
     * @return 收藏的动态列表与下一页游标
     */
    @GetMapping("/me/bookmarks")
    public ApiResponse<BookmarkListResponse> bookmarks(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.bookmarks(principal, cursor, limit));
    }
}
