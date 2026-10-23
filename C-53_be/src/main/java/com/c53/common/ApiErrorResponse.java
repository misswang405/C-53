package com.c53.common;

/**
 * 统一错误响应包装。
 *
 * <p>由 {@link GlobalExceptionHandler} 以及 Spring Security 的认证入口统一输出，
 * 保证无论错误发生在过滤器、控制器还是数据库层，客户端拿到的结构都一致。</p>
 *
 * @param code      机器可读的业务错误码，例如 {@code USERNAME_TAKEN}
 * @param message   面向用户的中文错误提示
 * @param details   可选的补充信息，例如字段校验明细、限流剩余时间
 * @param requestId 本次请求的追踪 ID
 */
public record ApiErrorResponse(String code, String message, Object details, String requestId) {
}
