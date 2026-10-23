package com.c53.modules.auth.controller;

import com.c53.modules.auth.service.AuthService;
import com.c53.modules.auth.dto.AuthDtos.MeResponse;
import com.c53.common.ApiResponse;
import com.c53.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口控制器。
 *
 * <p>同时注册新版路径 {@code /api/v1/me} 和兼容路径，必须携带有效 Access Token。
 * 认证由 Spring Security 统一完成，控制器拿到的 principal 一定非空（除非路由配置变更）。</p>
 */
@RestController
@RequestMapping({"/api/v1/me", "/api/v1/auth/me", "/me", "/auth/me"})
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 查询当前用户信息。
     *
     * @param principal 当前认证身份
     * @return 用户资料、会话和未读数
     */
    @GetMapping
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(authService.me(principal));
    }
}
