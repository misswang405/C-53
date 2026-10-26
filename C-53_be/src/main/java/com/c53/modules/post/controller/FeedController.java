package com.c53.modules.post.controller;

import com.c53.common.ApiResponse;
import com.c53.common.ClientIp;
import com.c53.modules.post.dto.PostDtos.PostListResponse;
import com.c53.modules.post.service.PostService;
import com.c53.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页信息流接口。
 *
 * <p>允许游客访问；{@code scope=FOLLOWING} 在 Service 层强制登录。
 * 匿名与登录分别使用不同维度的限流键。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class FeedController {

    private final PostService postService;

    public FeedController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 查询信息流。
     *
     * @param principal 当前用户，游客为 null
     * @param scope     范围：RECOMMENDED / FOLLOWING / LATEST
     * @param cursor    分页游标
     * @param limit     返回条数
     * @param request   当前 HTTP 请求，用于提取客户端 IP
     * @return 动态列表与下一页游标
     */
    @GetMapping("/feed")
    public ApiResponse<PostListResponse> feed(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        return ApiResponse.ok(postService.feed(
                principal, scope, cursor, limit, ClientIp.resolve(request)));
    }
}
