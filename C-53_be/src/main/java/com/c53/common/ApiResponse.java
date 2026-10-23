package com.c53.common;

/**
 * 统一成功响应包装。
 *
 * <p>所有 2xx 响应都通过该 record 序列化。{@code requestId} 用于把客户端问题
 * 与服务端日志关联起来，方便排查。</p>
 *
 * @param code      业务状态码，成功时固定为 {@code OK}
 * @param data      业务数据体，允许为 {@code null}
 * @param requestId 本次请求的追踪 ID
 * @param <T>       业务数据类型
 */
public record ApiResponse<T>(String code, T data, String requestId) {

    /**
     * 构造成功响应，并自动写入当前请求的追踪 ID。
     *
     * @param data 业务数据
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, RequestIds.current());
    }
}
