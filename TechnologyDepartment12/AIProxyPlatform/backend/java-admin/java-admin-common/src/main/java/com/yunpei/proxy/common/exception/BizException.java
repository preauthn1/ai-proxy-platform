package com.yunpei.proxy.common.exception;

import com.yunpei.proxy.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常基类。
 *
 * 所有受控的业务错误都应抛出 {@code BizException}，由
 * {@code GlobalExceptionHandler} 统一转 {@code Result}。
 *
 * @author yunpei
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务码（来自 ResultCode 或自定义）。 */
    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    public static BizException of(ResultCode code) {
        return new BizException(code);
    }

    public static BizException of(ResultCode code, String msg) {
        return new BizException(code, msg);
    }
}
