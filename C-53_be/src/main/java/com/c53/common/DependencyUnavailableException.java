package com.c53.common;

import org.springframework.http.HttpStatus;

/**
 * 外部依赖不可用异常。
 *
 * <p>当 Redis、数据库等基础设施连接失败时抛出，对应 HTTP 503。
 * 与普通业务异常区分开，便于监控告警和客户端决定是否重试。</p>
 */
public class DependencyUnavailableException extends ApiException {

    /**
     * 构造依赖不可用异常。
     *
     * @param message 错误提示
     */
    public DependencyUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    /**
     * 构造依赖不可用异常，并保留原始异常用于日志排查。
     *
     * @param message 错误提示
     * @param cause   原始异常
     */
    public DependencyUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
        initCause(cause);
    }
}
