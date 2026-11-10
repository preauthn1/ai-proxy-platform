package com.yunpei.proxy.service.llm;

/**
 * LLM 补全结果。
 *
 * @param content 模型输出正文
 * @param model 实际使用的模型 id（可为 null）
 * @author yunpei
 */
public record LlmResponse(String content, String model) {

    public static LlmResponse of(String content, String model) {
        return new LlmResponse(content, model);
    }
}
