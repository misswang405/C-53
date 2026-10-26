package com.c53.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取工具。
 *
 * <p>优先读取反向代理设置的 {@code X-Forwarded-For} 第一个地址，
 * 没有代理头时回退到连接地址。集中在一处实现，避免各 Controller 重复解析。</p>
 */
public final class ClientIp {

    private ClientIp() {
    }

    /**
     * 获取客户端 IP。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端 IP，无法获取时返回空字符串
     */
    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}
