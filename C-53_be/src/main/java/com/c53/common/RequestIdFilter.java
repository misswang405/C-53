package com.c53.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求追踪 ID 过滤器。
 *
 * <p>为每个请求确定一个 {@code X-Request-Id}：优先复用客户端传入的合法值，
 * 否则生成新 ID。ID 会写入日志 MDC 和响应头，供前后端联调时串联日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * 解析或生成请求 ID，并保证线程上下文在请求结束后被清理。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 后续过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 客户端可透传追踪 ID，但限制长度，防止日志注入或超长值
        String requestId = request.getHeader(RequestIds.HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            requestId = RequestIds.newRequestId();
        }
        // 写入 MDC，使后续日志自动带上 requestId
        MDC.put(RequestIds.MDC_KEY, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用线程，必须清理 MDC，避免污染下一个请求
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
