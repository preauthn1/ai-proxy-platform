package com.yunpei.proxy.common.constant;

/**
 * 请求安全协议相关 HTTP 头常量。
 *
 * @author yunpei
 */
public final class SecurityHeaders {

    public static final String REQUEST_TIMESTAMP = "X-Request-Timestamp";
    /** 兼容旧客户端头。 */
    public static final String TIMESTAMP_LEGACY = "X-Timestamp";

    public static final String REQUEST_ID = "X-Request-ID";
    public static final String REQUEST_ENCRYPTED_KEY = "X-Request-Encrypted-Key";
    public static final String REQUEST_SIGNATURE = "X-Request-Signature";
    /** 兼容旧客户端头。 */
    public static final String SIGN_LEGACY = "X-Sign";

    public static final String RESPONSE_IS_ENCRYPT = "X-Response-Is-Encrypt";
    public static final String LANGUAGE = "X-Language";

    public static final String FORWARDED_FOR = "X-Forwarded-For";
    public static final String REAL_IP = "X-Real-IP";
    public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
    public static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
    public static final String USER_AGENT = "User-Agent";
    public static final String REFERER = "Referer";

    private SecurityHeaders() {}
}
