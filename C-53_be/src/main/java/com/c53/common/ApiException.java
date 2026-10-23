package com.c53.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常基类。
 *
 * <p>业务代码通过抛出该异常表达“可预期的失败”，由
 * {@link GlobalExceptionHandler} 转换成对应的 HTTP 状态码和
 * {@link ApiErrorResponse}。它继承 {@link RuntimeException}，不会强制调用方捕获。</p>
 */
public class ApiException extends RuntimeException {

    /** 返回给客户端的 HTTP 状态码。 */
    private final HttpStatus status;

    /** 机器可读的业务错误码。 */
    private final String code;

    /** 可选的补充信息，序列化到错误响应的 {@code details} 字段。 */
    private final Object details;

    /**
     * 构造不带补充信息的业务异常。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 用户可读的错误提示
     */
    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    /**
     * 构造带补充信息的业务异常。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 用户可读的错误提示
     * @param details 补充信息，可为 {@code null}
     */
    public ApiException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }

    /**
     * 构造 400 参数错误异常。
     *
     * @param code    业务错误码
     * @param message 错误提示
     * @return 业务异常
     */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * 构造 401 未认证异常。
     *
     * @param code    业务错误码
     * @param message 错误提示
     * @return 业务异常
     */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /**
     * 构造 403 无权限异常。
     *
     * @param code    业务错误码
     * @param message 错误提示
     * @return 业务异常
     */
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    /**
     * 构造 409 资源冲突异常。
     *
     * @param code    业务错误码
     * @param message 错误提示
     * @return 业务异常
     */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /**
     * 构造 429 限流异常。
     *
     * @param code    业务错误码
     * @param message 错误提示
     * @param details 限流明细，例如剩余等待时间
     * @return 业务异常
     */
    public static ApiException tooManyRequests(String code, String message, Object details) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message, details);
    }
}
