package com.yunpei.proxy.common.exception;

import com.yunpei.proxy.common.result.ResultCode;
import lombok.Getter;

/**
 * 鉴权异常。
 *
 * <p>包装 Sa-Token 抛出的 {@code NotLoginException} / {@code NotRoleException}
 * 等，统一由 {@code GlobalExceptionHandler} 转 {@code Result}。
 *
 * @author yunpei
 */
@Getter
public class AuthException extends RuntimeException {

    private final int code;

    public AuthException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public AuthException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public AuthException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    public static AuthException notLogin() {
        return new AuthException(ResultCode.AUTH_NOT_LOGIN);
    }

    public static AuthException invalidCredentials() {
        return new AuthException(ResultCode.AUTH_INVALID_CREDENTIALS);
    }

    public static AuthException tokenExpired() {
        return new AuthException(ResultCode.AUTH_TOKEN_EXPIRED);
    }

    public static AuthException forbidden() {
        return new AuthException(ResultCode.AUTH_FORBIDDEN);
    }

    /** 访问黑名单命中；对外固定文案，不携带内部 reason。 */
    public static AuthException accessBlocked() {
        return new AuthException(ResultCode.ACCESS_BLOCKED);
    }
}
