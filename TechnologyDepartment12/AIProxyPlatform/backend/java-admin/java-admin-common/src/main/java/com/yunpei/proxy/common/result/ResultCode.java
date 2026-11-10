package com.yunpei.proxy.common.result;

import lombok.Getter;

/**
 * 业务码枚举。
 *
 * <p>分段规则（Q13 决策后，移除 3xxx 限流段）：
 * <ul>
 *     <li>{@code 0} 成功</li>
 *     <li>{@code 1xxx} 通用（参数/远程/内部）与请求安全（过期/错误/密钥/Nonce/签名）</li>
 *     <li>{@code 2xxx} 鉴权（2001 未登录 / 2002 凭证错误 / 2003 token 过期 / 2004 无权限 / 2005 Access Blocked）</li>
 *     <li>{@code 4xxx} 业务（保留）</li>
 * </ul>
 *
 * @author yunpei
 */
@Getter
public enum ResultCode {
    SUCCESS(0, "ok"),

    PARAM_INVALID(1001, "参数错误"),
    REMOTE_CALL_FAILED(1002, "远程调用失败"),
    INTERNAL_ERROR(1003, "内部错误"),
    /** 请求时间窗过期（Timestamp 中间件）。 */
    REQUEST_EXPIRED(1004, "请求已过期"),
    /** 请求格式/必填安全头错误（如缺加密头、非法时间戳）。 */
    REQUEST_ERROR(1005, "请求错误"),
    /** RSA/AES 密钥或解密失败。 */
    REQUEST_KEY_FAILED(1006, "密钥错误"),
    /** Nonce 重放（同一 X-Request-ID 在有效期内重复）。 */
    REQUEST_NONCE_CONFLICT(1007, "请求重复"),
    /** 签名校验失败（Encrypt 关闭时的独立 Sign 中间件）。 */
    REQUEST_SIGN_FAILED(1008, "签名错误"),

    AUTH_NOT_LOGIN(2001, "请登录"),
    AUTH_INVALID_CREDENTIALS(2002, "凭证错误"),
    AUTH_TOKEN_EXPIRED(2003, "登录已过期"),
    AUTH_FORBIDDEN(2004, "无权限"),
    /** 访问黑名单命中（Access Blocked）；固定文案，不回传内部 reason。 */
    ACCESS_BLOCKED(2005, "Access Blocked");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
