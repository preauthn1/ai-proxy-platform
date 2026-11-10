package com.yunpei.proxy.service.video;

/**
 * 视频 Provider 调用失败。
 *
 * @author yunpei
 */
public class VideoProviderException extends RuntimeException {

    private final int httpStatus;

    public VideoProviderException(String message) {
        this(message, -1, null);
    }

    public VideoProviderException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public VideoProviderException(String message, int httpStatus) {
        this(message, httpStatus, null);
    }

    public VideoProviderException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP 状态码；非 HTTP 错误时为 {@code -1}。 */
    public int httpStatus() {
        return httpStatus;
    }
}
