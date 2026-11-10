package com.yunpei.proxy.service.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM Provider 配置（对应 {@code llm.*}）。
 *
 * <p>密钥仅通过环境变量注入，禁止写入仓库。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProviderProperties {

    /**
     * Provider 名称：{@code mock}（默认，单测/本地）；{@code openai-compatible} 或 {@code new2api}
     *（均走 OpenAI 兼容 chat/completions，配置仍读 {@code llm.openai-compatible}）。
     */
    private String provider = "mock";

    /** OpenAI 兼容配置。 */
    private OpenAiCompatible openaiCompatible = new OpenAiCompatible();

    /** 是否使用 mock Provider。 */
    public boolean isMock() {
        String value = provider == null ? "" : provider.trim();
        return value.isEmpty() || "mock".equalsIgnoreCase(value);
    }

    /** 是否使用 OpenAI 兼容 Provider（含 new2api 别名）。 */
    public boolean isOpenAiCompatible() {
        String value = provider == null ? "" : provider.trim();
        return "openai-compatible".equalsIgnoreCase(value) || "new2api".equalsIgnoreCase(value);
    }

    /** OpenAI 兼容 chat/completions 配置。 */
    @Data
    public static class OpenAiCompatible {

        /** API Key；对应 {@code LLM_API_KEY}。 */
        private String apiKey;

        /** OpenAI 兼容 base URL（须含 /v1）。 */
        private String baseUrl = "http://127.0.0.1:3001/v1";

        /** 默认模型 id。 */
        private String model = "gpt-4o-mini";

        /** HTTP 超时秒数。 */
        private int timeoutSeconds = 120;
    }
}
