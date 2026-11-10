package com.yunpei.proxy.service.externalai;

/**
 * AI 文本生成任务未找到（GET 未命中或 task_type 不在文本类集合）。
 *
 * <p>由 Controller 局部 {@code @ExceptionHandler} 映射为 HTTP 404 + 外部信封。
 *
 * @author yunpei
 */
public class AiGenerationTaskNotFoundException extends RuntimeException {

    public AiGenerationTaskNotFoundException(String message) {
        super(message);
    }
}
