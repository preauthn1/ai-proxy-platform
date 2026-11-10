package com.yunpei.proxy.service.llm;

/**
 * LLM HTTP 调用超时，供 Temporal activity 识别后重试。
 *
 * @author yunpei
 */
public class LlmTimeoutException extends LlmProviderException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
