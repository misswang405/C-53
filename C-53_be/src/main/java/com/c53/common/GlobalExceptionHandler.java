package com.c53.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * 全局异常处理器。
 *
 * <p>把所有从控制器层抛出的异常转换为统一的 {@link ApiErrorResponse}，
 * 避免堆栈信息或数据库细节直接暴露给客户端。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务代码主动抛出的 {@link ApiException}。
     *
     * <p>这类异常属于可预期失败，只记录 info 级别日志，不打印堆栈。</p>
     *
     * @param exception 业务异常
     * @return 带业务状态码和错误码的响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        log.info("auth request rejected code={} status={}", exception.getCode(), exception.getStatus().value());
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage(), exception.getDetails(), RequestIds.current()));
    }

    /**
     * 处理请求体字段校验失败（{@code @Valid} 触发）。
     *
     * <p>当前统一返回一个泛化的字段错误，避免暴露内部字段结构。</p>
     *
     * @param exception 校验异常
     * @return 400 校验错误响应
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception exception) {
        List<Map<String, Object>> details = List.of(Map.of("field", "request", "reason", "invalid"));
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", "请求字段缺失或格式错误", details, RequestIds.current()));
    }

    /**
     * 处理请求体无法解析、缺少查询参数等格式问题。
     *
     * @param exception 解析异常
     * @return 400 格式错误响应
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleUnreadable(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", "请求格式不正确", null, RequestIds.current()));
    }

    /**
     * 处理数据库唯一约束冲突。
     *
     * <p>根据 MySQL 约束名映射为具体业务错误码，让前端可以定位到用户名、
     * 联系方式或设备会话冲突。</p>
     *
     * @param exception 数据完整性异常
     * @return 409 冲突响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        String normalized = message == null ? "" : message.toLowerCase();
        // 用户名唯一索引冲突
        if (normalized.contains("uk_users_username")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("USERNAME_TAKEN", "用户名已被使用", null, RequestIds.current()));
        }
        // 邮箱/手机号唯一索引冲突
        if (normalized.contains("uk_uc_value_active")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("CONTACT_TAKEN", "邮箱或手机号已注册", null, RequestIds.current()));
        }
        // 同一设备重复创建有效会话
        if (normalized.contains("uk_as_device_user_active")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("SESSION_CONFLICT", "该设备已有有效会话，请重试", null, RequestIds.current()));
        }
        // 未识别的约束冲突，记录日志并返回通用提示
        log.warn("database constraint violation", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("CONFLICT", "数据冲突，请稍后重试", null, RequestIds.current()));
    }

    /**
     * 处理其他数据库访问异常，例如连接失败、SQL 语法错误。
     *
     * @param exception 数据访问异常
     * @return 500 内部错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException exception) {
        log.error("database access failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "服务内部异常", null, RequestIds.current()));
    }

    /**
     * 处理静态资源或未匹配路由，返回 404 而不是默认错误页。
     *
     * @param exception 资源不存在异常
     * @return 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("NOT_FOUND", "请求的资源不存在", null, RequestIds.current()));
    }

    /**
     * 兜底处理所有未预期异常。
     *
     * <p>打印完整堆栈便于排查，但对外只返回通用提示，防止信息泄露。</p>
     *
     * @param exception 未捕获异常
     * @return 500 内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "服务内部异常", null, RequestIds.current()));
    }
}
