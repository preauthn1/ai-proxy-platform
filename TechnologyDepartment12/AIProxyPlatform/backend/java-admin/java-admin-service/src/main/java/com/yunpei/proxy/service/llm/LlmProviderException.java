package com.yunpei.proxy.service.llm;

/**
 * LLM Provider 调用失败。
 *
 * @author yunpei
 */
public class LlmProviderException extends RuntimeException {

    public LlmProviderException(String message) {
        super(message);
    }

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
