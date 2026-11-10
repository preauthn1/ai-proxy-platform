package com.yunpei.proxy.common.util;

import java.util.UUID;

/**
 * traceId 工具。
 *
 * <p>从 {@code X-Trace-Id} header 读取，缺失则生成 UUID。MDC key 见 {@link com.yunpei.proxy.common.constant.MdcKeys#TRACE_ID}。
 *
 * @author yunpei
 */
public final class TraceIdUtil {

    /** HTTP 请求/响应头名 */
    public static final String HEADER = "X-Trace-Id";

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String resolveOrGenerate(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return generate();
        }
        return headerValue;
    }

    private TraceIdUtil() {}
}
