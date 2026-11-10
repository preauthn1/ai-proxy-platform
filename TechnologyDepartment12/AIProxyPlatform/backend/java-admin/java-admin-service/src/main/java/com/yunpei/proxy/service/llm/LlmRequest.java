package com.yunpei.proxy.service.llm;

/**
 * LLM 补全请求（任务执行路径使用；GET 禁止调用 Provider）。
 *
 * @param systemPrompt 系统提示；可为 null
 * @param userPrompt 用户提示；必填
 * @param purpose 用途标签（便于 mock / 日志），如 {@code topics} / {@code copy}
 * @author yunpei
 */
public record LlmRequest(String systemPrompt, String userPrompt, String purpose) {

    public static LlmRequest of(String systemPrompt, String userPrompt, String purpose) {
        return new LlmRequest(systemPrompt, userPrompt, purpose);
    }
}
