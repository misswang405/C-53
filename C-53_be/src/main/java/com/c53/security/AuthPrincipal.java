package com.c53.security;

/**
 * 已认证用户身份。
 *
 * <p>由 JWT 过滤器解析后放入 Spring Security 上下文，控制器通过
 * {@code @AuthenticationPrincipal} 获取。</p>
 *
 * @param userId    用户 ID
 * @param sessionId 当前登录会话 ID，用于刷新令牌轮换和会话撤销
 */
public record AuthPrincipal(String userId, String sessionId) {
}
