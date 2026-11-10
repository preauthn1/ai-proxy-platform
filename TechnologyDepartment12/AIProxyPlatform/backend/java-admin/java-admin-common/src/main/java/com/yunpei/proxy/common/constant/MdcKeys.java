package com.yunpei.proxy.common.constant;

/**
 * SLF4J MDC key 统一存放处。
 *
 * <p>与 logback pattern（如 {@code %X{traceId}}）及各 Filter/Aspect 写入的 key 保持一致。
 *
 * @author yunpei
 */
public final class MdcKeys {

    /** 请求链路 ID（与 logback {@code %X{traceId}} 一致） */
    public static final String TRACE_ID = "traceId";

    /** 当前登录用户 ID（与 logback {@code %X{userId}} 一致） */
    public static final String USER_ID = "userId";

    private MdcKeys() {}
}
