package com.yunpei.proxy.service.externalimage;

/**
 * 图片生成任务未找到（GET /jobs/{jobId} 未命中或 task_type 不匹配）。
 *
 * <p>由 {@code ExternalImageGenerationController} 局部 {@code @ExceptionHandler} 映射为 HTTP 404 + 外部信封，
 * 不落入全局 {@code Result} 信封。
 *
 * @author yunpei
 */
public class GenerationTaskNotFoundException extends RuntimeException {

    public GenerationTaskNotFoundException(String message) {
        super(message);
    }
}
