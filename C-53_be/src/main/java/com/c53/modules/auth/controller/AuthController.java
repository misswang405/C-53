package com.c53.modules.auth.controller;

import com.c53.modules.auth.service.AuthService;
import com.c53.modules.auth.service.AuthService.AuthResult;
import com.c53.modules.auth.service.AuthService.RequestMeta;
import com.c53.modules.auth.service.VerificationService;
import com.c53.modules.auth.dto.AuthDtos.*;
import com.c53.common.ApiResponse;
import com.c53.common.ClientIp;
import com.c53.common.RequestIds;
import com.c53.config.AuthProperties;
import com.c53.security.AuthPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证接口控制器。
 *
 * <p>同时注册 {@code /api/v1/auth} 和兼容前缀 {@code /auth}。
 * 控制器只做三件事：提取请求上下文、调用 Service、把设备 Cookie 写回响应。
 * 业务校验和事务均在 Service 层完成。</p>
 */
@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, VerificationService verificationService,
                          AuthProperties properties) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.properties = properties;
    }

    /**
     * 发送验证码。
     *
     * <p>兼容旧字段：{@code target/channel} 缺失时回退到 {@code identifier/identifierType}。</p>
     *
     * @param request        验证码请求
     * @param servletRequest 当前 HTTP 请求
     * @return 202 + 验证码发送信息
     */
    @PostMapping({"/verification-codes", "/send-code"})
    public ResponseEntity<ApiResponse<VerificationCodeResponse>> sendVerificationCode(
            @RequestBody VerificationCodeRequest request, HttpServletRequest servletRequest) {
        // 新旧字段择一：新字段优先
        String target = request.target() != null ? request.target() : request.identifier();
        String channel = request.channel() != null ? request.channel() : request.identifierType();
        VerificationCodeResponse response = verificationService.send(
                request.scene(), channel, target, null, RequestIds.current(),
                clientIp(servletRequest), userAgent(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    /** 通过重置挑战更新密码，成功不自动创建新会话。 */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request,
                                               HttpServletRequest servletRequest) {
        authService.resetPassword(request, meta(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /** 注册成功后建立登录会话并下发设备 Cookie。 */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> register(
            @RequestBody RegisterRequest request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthResult<AuthSessionResponse> result = authService.register(request, meta(servletRequest));
        // 首次注册可能同时创建设备，需要把新 Cookie 下发
        writeDeviceCookie(servletResponse, result.deviceCookie());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result.data()));
    }

    /**
     * 检查用户名是否可用。
     *
     * @param username 原始用户名
     * @return 规范化结果与可用性
     */
    @GetMapping("/username-availability")
    public ApiResponse<UsernameAvailabilityResponse> usernameAvailability(@RequestParam String username) {
        return ApiResponse.ok(authService.usernameAvailability(username));
    }

    /**
     * 密码登录。
     *
     * @param request         登录请求
     * @param servletRequest  当前 HTTP 请求
     * @param servletResponse 当前 HTTP 响应
     * @return 会话信息
     */
    @PostMapping("/login")
    public ApiResponse<AuthSessionResponse> login(@RequestBody LoginRequest request,
                                                  HttpServletRequest servletRequest,
                                                  HttpServletResponse servletResponse) {
        AuthResult<AuthSessionResponse> result = authService.login(request, meta(servletRequest));
        writeDeviceCookie(servletResponse, result.deviceCookie());
        return ApiResponse.ok(result.data());
    }

    /**
     * 组装 Service 层需要的请求上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 请求上下文
     */
    private RequestMeta meta(HttpServletRequest request) {
        return new RequestMeta(RequestIds.current(), clientIp(request), userAgent(request),
                deviceCookie(request));
    }

    /**
     * 从 Cookie 中读取设备令牌。
     *
     * @param request 当前 HTTP 请求
     * @return 设备令牌；不存在时返回 {@code null}
     */
    private String deviceCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getDevice().getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 下发设备 Cookie。
     *
     * <p>使用 HttpOnly 防止脚本读取，SameSite=Lax 兼顾跨站跳转和 CSRF 防护。
     * 值为 {@code null} 时不下发，避免覆盖客户端已有 Cookie。</p>
     *
     * @param response 当前 HTTP 响应
     * @param value    新的设备令牌
     */
    private void writeDeviceCookie(HttpServletResponse response, String value) {
        if (value == null) {
            return;
        }
        Duration ttl = properties.getDevice().getCookieTtl();
        ResponseCookie cookie = ResponseCookie.from(properties.getDevice().getCookieName(), value)
                .httpOnly(true)
                .secure(properties.getDevice().isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String clientIp(HttpServletRequest request) {
        return ClientIp.resolve(request);
    }

    /**
     * 获取 User-Agent，并截断到 500 字符，防止超长头写入数据库。
     *
     * @param request 当前 HTTP 请求
     * @return User-Agent 或 {@code null}
     */
    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader(HttpHeaders.USER_AGENT);
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    /**
     * 验证码登录。
     *
     * @param request         验证码登录请求
     * @param servletRequest  当前 HTTP 请求
     * @param servletResponse 当前 HTTP 响应
     * @return 会话信息
     */
    @PostMapping("/login/code")
    public ApiResponse<AuthSessionResponse> loginByCode(@RequestBody CodeLoginRequest request,
                                                        HttpServletRequest servletRequest,
                                                        HttpServletResponse servletResponse) {
        AuthResult<AuthSessionResponse> result = authService.loginByCode(request, meta(servletRequest));
        writeDeviceCookie(servletResponse, result.deviceCookie());
        return ApiResponse.ok(result.data());
    }

    /**
     * 刷新令牌。
     *
     * @param request        刷新请求
     * @param servletRequest 当前 HTTP 请求
     * @return 新令牌
     */
    @PostMapping({"/refresh", "/token/refresh"})
    public ApiResponse<RefreshResponse> refresh(@RequestBody RefreshRequest request,
                                                HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.refresh(request, meta(servletRequest)));
    }

    /**
     * 退出登录。
     *
     * @param principal 当前认证身份
     * @param request   可选的 Refresh Token
     * @return 204 无内容
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestBody(required = false) LogoutRequest request) {
        authService.logout(principal, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询当前设备已保存的账号。
     *
     * @param principal      当前认证身份，可为 {@code null}
     * @param servletRequest 当前 HTTP 请求
     * @return 已保存账号列表
     */
    @GetMapping("/accounts")
    public ApiResponse<SavedAccountsResponse> accounts(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest) {
        String currentSessionId = principal == null ? null : principal.sessionId();
        return ApiResponse.ok(authService.accounts(
                deviceCookie(servletRequest), currentSessionId, meta(servletRequest)));
    }

    /**
     * 切换账号。
     *
     * @param request         切换请求
     * @param servletRequest  当前 HTTP 请求
     * @param servletResponse 当前 HTTP 响应
     * @return 新会话信息
     */
    @PostMapping("/switch")
    public ApiResponse<AuthSessionResponse> switchAccount(@RequestBody SwitchRequest request,
                                                          HttpServletRequest servletRequest,
                                                          HttpServletResponse servletResponse) {
        AuthResult<AuthSessionResponse> result = authService.switchAccount(request, meta(servletRequest));
        writeDeviceCookie(servletResponse, result.deviceCookie());
        return ApiResponse.ok(result.data());
    }
}
