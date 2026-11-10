package com.yunpei.proxy.common.request;

/**
 * 请求级 ThreadLocal 上下文：业务代码通过此类读取当前用户、语言等，避免到处查 Sa-Token / Request。
 *
 * <p>生命周期由 {@code RequestContextFilter} 负责 open/close；字段由安全链路按序写入。
 * 异步线程<strong>不会</strong>自动继承，跨线程需自行传递快照。
 *
 * <p>典型用法：
 * <pre>{@code
 * Long userId = RequestContext.userIdOrNull();
 * String lang = RequestContext.languageOrNull();
 * }</pre>
 *
 * @author yunpei
 */
public final class RequestContext {

    private static final ThreadLocal<RequestInfo> HOLDER = new ThreadLocal<>();

    private RequestContext() {}

    /** 打开空上下文（Filter 入口调用）。已存在则复用。 */
    public static void open() {
        if (HOLDER.get() == null) {
            HOLDER.set(new RequestInfo());
        }
    }

    /** 清理 ThreadLocal，防止线程池泄漏（Filter finally 调用）。 */
    public static void close() {
        HOLDER.remove();
    }

    /**
     * 当前请求信息；未 open 时返回 null。
     *
     * <p>业务侧优先用 {@link #userIdOrNull()} / {@link #languageOrNull()} 等快捷方法。
     */
    public static RequestInfo get() {
        return HOLDER.get();
    }

    /** 当前请求信息；未 open 时惰性创建（便于单测），生产仍应由 Filter open。 */
    public static RequestInfo getOrCreate() {
        RequestInfo info = HOLDER.get();
        if (info == null) {
            info = new RequestInfo();
            HOLDER.set(info);
        }
        return info;
    }

    public static Long userIdOrNull() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.getUserId();
    }

    public static String languageOrNull() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.getLanguage();
    }

    public static String requestIdOrNull() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.getRequestId();
    }

    public static String requestUriOrNull() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.getRequestUri();
    }

    public static String clientIpOrNull() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.getClientIp();
    }

    public static void setUserId(Long userId) {
        getOrCreate().setUserId(userId);
    }

    public static void setLanguage(String language) {
        getOrCreate().setLanguage(language);
    }

    public static void setRequestId(String requestId) {
        getOrCreate().setRequestId(requestId);
    }

    public static void setRequestUri(String requestUri) {
        getOrCreate().setRequestUri(requestUri);
    }

    public static void setClientIp(String clientIp) {
        getOrCreate().setClientIp(clientIp);
    }
}
