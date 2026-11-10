package com.yunpei.proxy.common.request;

import lombok.Data;

/**
 * 单次 HTTP 请求的上下文快照（存于 {@link RequestContext} ThreadLocal）。
 *
 * <p>字段按需填充：Filter 阶段可写 requestId/URI；认证后写 userId；Language 中间件写 language。
 *
 * @author yunpei
 */
@Data
public class RequestInfo {

    /** 当前登录用户 ID；未登录为 null。 */
    private Long userId;

    /** 请求语言（X-Language / Accept-Language）；未解析为 null。 */
    private String language;

    /** 请求 ID（X-Request-ID）；可作 nonce。 */
    private String requestId;

    /** 请求 URI（不含 query）。 */
    private String requestUri;

    /** 客户端 IP（若已解析）。 */
    private String clientIp;
}
