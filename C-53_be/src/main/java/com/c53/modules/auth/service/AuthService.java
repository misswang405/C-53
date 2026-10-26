package com.c53.modules.auth.service;

import com.c53.modules.auth.dto.AuthDtos.CodeLoginRequest;
import com.c53.modules.auth.dto.AuthDtos.LoginRequest;
import com.c53.modules.auth.dto.AuthDtos.LogoutRequest;
import com.c53.modules.auth.dto.AuthDtos.MeResponse;
import com.c53.modules.auth.dto.AuthDtos.RefreshRequest;
import com.c53.modules.auth.dto.AuthDtos.RefreshResponse;
import com.c53.modules.auth.dto.AuthDtos.RegisterRequest;
import com.c53.modules.auth.dto.AuthDtos.ResetPasswordRequest;
import com.c53.modules.auth.dto.AuthDtos.SavedAccountsResponse;
import com.c53.modules.auth.dto.AuthDtos.SwitchRequest;
import com.c53.modules.auth.dto.AuthDtos.UsernameAvailabilityResponse;
import com.c53.security.AuthPrincipal;

/**
 * 认证主流程服务。
 *
 * <p>覆盖注册、密码/验证码登录、刷新、退出、当前用户、已保存账号和切换账号。
 * Controller 只负责 HTTP 映射，所有业务校验、事务和安全策略都在实现类中完成。</p>
 */
public interface AuthService {

    /** 验证重置挑战、更新密码并撤销该用户全部旧会话。 */
    void resetPassword(ResetPasswordRequest request, RequestMeta meta);

    /**
     * 注册新用户并直接建立登录会话。
     *
     * @param request 注册请求
     * @param meta    请求上下文
     * @return 会话数据和可能下发的设备 Cookie
     */
    AuthResult<com.c53.modules.auth.dto.AuthDtos.AuthSessionResponse> register(
            RegisterRequest request, RequestMeta meta);

    /**
     * 使用 Refresh Token 换取新的 Access/Refresh Token。
     *
     * @param request 刷新请求
     * @param meta    请求上下文
     * @return 新令牌
     */
    RefreshResponse refresh(RefreshRequest request, RequestMeta meta);

    /**
     * 退出当前会话。
     *
     * @param principal 当前认证身份
     * @param request   可选的 Refresh Token；提供时吊销整个令牌家族
     */
    void logout(AuthPrincipal principal, LogoutRequest request);

    /**
     * 查询当前设备上保存的账号列表。
     *
     * @param deviceCookie     设备 Cookie 原始值
     * @param currentSessionId 当前会话 ID，用于标记 current
     * @param meta             请求上下文
     * @return 已保存账号列表
     */
    SavedAccountsResponse accounts(String deviceCookie, String currentSessionId, RequestMeta meta);

    /**
     * 在当前设备上切换到另一个已保存账号。
     *
     * @param request 切换请求
     * @param meta    请求上下文
     * @return 新会话数据和可能下发的设备 Cookie
     */
    AuthResult<com.c53.modules.auth.dto.AuthDtos.AuthSessionResponse> switchAccount(
            SwitchRequest request, RequestMeta meta);

    /**
     * 查询当前用户信息、会话和未读数。
     *
     * @param principal 当前认证身份
     * @return 当前用户信息
     */
    MeResponse me(AuthPrincipal principal);

    /**
     * 检查用户名是否可用。
     *
     * @param username 原始用户名
     * @return 规范化用户名及可用性
     */
    UsernameAvailabilityResponse usernameAvailability(String username);

    /**
     * 使用账号和密码登录。
     *
     * @param request 登录请求
     * @param meta    请求上下文
     * @return 会话数据和可能下发的设备 Cookie
     */
    AuthResult<com.c53.modules.auth.dto.AuthDtos.AuthSessionResponse> login(
            LoginRequest request, RequestMeta meta);

    /**
     * 使用短信/邮箱验证码登录。
     *
     * @param request 验证码登录请求
     * @param meta    请求上下文
     * @return 会话数据和可能下发的设备 Cookie
     */
    AuthResult<com.c53.modules.auth.dto.AuthDtos.AuthSessionResponse> loginByCode(
            CodeLoginRequest request, RequestMeta meta);

    /**
     * 请求上下文。
     *
     * @param requestId    请求追踪 ID
     * @param ip           客户端 IP
     * @param userAgent    客户端 User-Agent
     * @param deviceCookie 设备 Cookie 原始值
     */
    record RequestMeta(String requestId, String ip, String userAgent, String deviceCookie) {
    }

    /**
     * 业务结果：数据 + 需要写回客户端的新设备 Cookie。
     *
     * <p>设备 Cookie 为 {@code null} 表示无需更新，避免每次请求都重复下发。</p>
     *
     * @param data         业务数据
     * @param deviceCookie 新设备 Cookie 值
     * @param <T>          数据类型
     */
    record AuthResult<T>(T data, String deviceCookie) {
    }
}
