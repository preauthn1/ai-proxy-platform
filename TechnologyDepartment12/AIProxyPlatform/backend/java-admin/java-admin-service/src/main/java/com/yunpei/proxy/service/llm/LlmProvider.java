package com.yunpei.proxy.service.llm;

/**
 * 文本 LLM Provider 抽象：真实 OpenAI 兼容实现 + Mock。
 *
 * <p>仅由任务执行路径调用，禁止在 GET 查询路径调用。
 *
 * @author yunpei
 */
public interface LlmProvider {

    /**
     * 执行一次文本补全。
     *
     * @param request 补全请求
     * @return 补全结果
     */
    LlmResponse complete(LlmRequest request);
}
