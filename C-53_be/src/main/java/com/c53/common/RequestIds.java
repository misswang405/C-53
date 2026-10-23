package com.c53.common;

import com.c53.common.support.IdGenerator;
import org.slf4j.MDC;

/**
 * 请求 ID 工具类。
 *
 * <p>约定请求头名 {@code X-Request-Id} 和 MDC 键名 {@code requestId}，
 * 供过滤器、异常处理和日志配置共同使用。</p>
 */
public final class RequestIds {

    /** MDC 中保存请求 ID 的键名。 */
    public static final String MDC_KEY = "requestId";

    /** HTTP 请求/响应头名称。 */
    public static final String HEADER = "X-Request-Id";

    private RequestIds() {
    }

    /**
     * 获取当前请求的 ID。
     *
     * <p>不在请求线程中时（例如异步任务）返回新生成的 ID，保证返回值永不为空。</p>
     *
     * @return 当前请求 ID
     */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? IdGenerator.newId("req") : value;
    }

    /**
     * 生成新的请求 ID。
     *
     * @return 形如 {@code req_<ULID>} 的 ID
     */
    public static String newRequestId() {
        return IdGenerator.newId("req");
    }
}
