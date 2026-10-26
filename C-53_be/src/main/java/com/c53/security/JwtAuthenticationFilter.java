package com.c53.security;

import com.c53.common.presence.OnlinePresenceRecorder;
import com.c53.common.ApiException;
import com.c53.common.ApiErrorResponse;
import com.c53.common.RequestIds;
import com.c53.modules.auth.service.AuthSessionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>从 {@code Authorization: Bearer <token>} 中解析 Access Token。
 * 解析失败时不会立即返回错误，而是把失败原因写入请求属性，
 * 由 Spring Security 的认证入口统一决定是否拦截当前路由。
 * 这样公开接口即使携带过期 Token 也能正常访问。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性名：保存 Token 解析失败的原因。 */
    public static final String TOKEN_ERROR_ATTRIBUTE = "c53.auth.tokenError";

    /** Token 过期错误码。 */
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";

    /** Token 非法错误码。 */
    public static final String INVALID_ACCESS_TOKEN = "INVALID_ACCESS_TOKEN";

    private final JwtService jwtService;
    private final OnlinePresenceRecorder onlinePresenceRecorder;
    private final AuthSessionGuard sessionGuard;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, OnlinePresenceRecorder onlinePresenceRecorder,
                                  AuthSessionGuard sessionGuard,
                                  ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.onlinePresenceRecorder = onlinePresenceRecorder;
        this.sessionGuard = sessionGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * 尝试解析 Bearer Token，成功则写入安全上下文。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 后续过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            // 去掉 "Bearer " 前缀和可能存在的空白
            String token = header.substring(7).trim();
            try {
                JwtService.AccessTokenClaims claims = jwtService.parse(token);
                sessionGuard.requireActive(claims.userId(), claims.sessionId());
                AuthPrincipal principal = new AuthPrincipal(claims.userId(), claims.sessionId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                // 认证成功：后续 Controller 可直接获取当前用户
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 在线人数统计，内部按用户做写入节流，异常不影响主流程
                onlinePresenceRecorder.touch(claims.userId());
            } catch (ApiException exception) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, exception.getCode());
                SecurityContextHolder.clearContext();
            } catch (DataAccessException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(503);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                        "DEPENDENCY_UNAVAILABLE", "认证服务暂时不可用，请稍后重试", null,
                        RequestIds.current()));
                return;
            } catch (ExpiredJwtException exception) {
                // 区分“过期”和“非法”，便于前端决定刷新还是重新登录
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, TOKEN_EXPIRED);
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException exception) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, INVALID_ACCESS_TOKEN);
                SecurityContextHolder.clearContext();
            }
        }
        // 即使没有 Token 也继续执行，由 SecurityConfig 中的路由规则决定是否放行
        filterChain.doFilter(request, response);
    }
}
