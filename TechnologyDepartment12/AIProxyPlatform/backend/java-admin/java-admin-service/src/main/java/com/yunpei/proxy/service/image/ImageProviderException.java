package com.yunpei.proxy.service.image;

/**
 * 图片生成 Provider 失败（配置缺失、上游 4xx/5xx、响应无图等）。
 *
 * <p>由 {@link com.yunpei.proxy.service.externalimage.ExternalImageGenerationService#execute} 捕获后
 * 回写 {@code failed} 状态。
 *
 * @author yunpei
 */
public class ImageProviderException extends RuntimeException {

    /**
     * @param message 错误说明（写入 error_message，会被截断）
     */
    public ImageProviderException(String message) {
        super(message);
    }

    /**
     * @param message 错误说明
     * @param cause 根因
     */
    public ImageProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
