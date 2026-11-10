package com.yunpei.proxy.service.auth;

/**
 * 登录请求客户端元信息（写 login_log 用）。
 *
 * @param loginIp   客户端 IP
 * @param userAgent User-Agent
 * @author yunpei
 */
public record LoginClientMeta(String loginIp, String userAgent) {

    public static LoginClientMeta empty() {
        return new LoginClientMeta("", "");
    }

    public LoginClientMeta {
        loginIp = loginIp == null ? "" : loginIp;
        userAgent = userAgent == null ? "" : userAgent;
    }
}
