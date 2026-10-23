package com.c53.security;

import com.c53.common.ApiErrorResponse;
import com.c53.common.RequestIds;
import com.c53.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security 配置。
 *
 * <p>应用是无状态的 JWT 认证：关闭 Session、表单登录和 CSRF，
 * 由 {@link JwtAuthenticationFilter} 解析 Bearer Token。
 * 只有退出登录和当前用户接口需要认证，其余认证相关接口保持公开。</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 密码编码器。
     *
     * <p>使用 BCrypt 强度 12，兼顾安全性和登录耗时。</p>
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 配置安全过滤器链。
     *
     * @param http                  HttpSecurity 构建器
     * @param jwtAuthenticationFilter JWT 认证过滤器
     * @param objectMapper          JSON 序列化器，用于输出统一错误响应
     * @return 安全过滤器链
     * @throws Exception 配置失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                // 前后端分离 + Bearer Token，不需要 CSRF 和传统登录方式
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 不创建服务端 Session，认证状态完全由 JWT 承载
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 退出登录需要知道当前会话，必须已认证
                        .requestMatchers("/api/v1/auth/logout", "/auth/logout").authenticated()
                        // 当前用户信息需要已认证
                        .requestMatchers("/api/v1/me", "/me", "/auth/me").authenticated()
                        // 登录、注册、刷新、验证码等接口保持公开
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeUnauthorized(request, response, objectMapper))
                        .accessDeniedHandler((request, response, exception) ->
                                writeForbidden(response, objectMapper)))
                // 在用户名密码认证过滤器之前执行 JWT 解析
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 配置跨域规则。
     *
     * <p>允许携带凭证（设备 Cookie），因此不能使用通配符 Origin，
     * 必须显式列出前端地址。</p>
     *
     * @param properties 认证模块配置
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // 暴露请求追踪头，方便前端读取并反馈给后端排查
        configuration.setExposedHeaders(List.of(RequestIds.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 输出 401 未认证响应。
     *
     * <p>结合过滤器中记录的失败原因，区分 Token 过期和未登录。</p>
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param mapper   JSON 序列化器
     * @throws IOException 写出响应失败
     */
    private static void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, ObjectMapper mapper)
            throws IOException {
        Object error = request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        String code = error instanceof String value ? value : "UNAUTHORIZED";
        String message = JwtAuthenticationFilter.TOKEN_EXPIRED.equals(code)
                ? "Access Token 已过期"
                : "请先登录";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse(code, message, null, RequestIds.current()));
    }

    /**
     * 输出 403 无权限响应。
     *
     * @param response 当前响应
     * @param mapper   JSON 序列化器
     * @throws IOException 写出响应失败
     */
    private static void writeForbidden(HttpServletResponse response, ObjectMapper mapper) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse("FORBIDDEN", "没有访问权限", null, RequestIds.current()));
    }
}
