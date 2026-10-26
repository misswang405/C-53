package com.c53.modules.discovery.controller;

import com.c53.common.ApiResponse;
import com.c53.common.ClientIp;
import com.c53.modules.discovery.dto.CreatorDtos.CreatorListResponse;
import com.c53.modules.discovery.service.CreatorService;
import com.c53.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推荐创作者接口。
 */
@RestController
@RequestMapping("/api/v1")
public class CreatorController {

    private final CreatorService creatorService;

    public CreatorController(CreatorService creatorService) {
        this.creatorService = creatorService;
    }

    /**
     * 推荐创作者。登录态会排除自己与已关注用户。
     *
     * @param principal 当前用户，游客为 null
     * @param request   当前 HTTP 请求，用于限流
     * @return 推荐创作者列表
     */
    @GetMapping("/creators/recommended")
    public ApiResponse<CreatorListResponse> recommended(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest request) {
        return ApiResponse.ok(creatorService.recommended(principal, ClientIp.resolve(request)));
    }
}
