package com.yunpei.proxy.service.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link LlmProvider} 工厂：按 {@link LlmProviderProperties#getProvider()} 路由。
 *
 * <p>{@code mock}（默认）→ {@link MockLlmProvider}；{@code openai-compatible} /
 * {@code new2api} → {@link OpenAiCompatibleLlmProvider}。
 *
 * @author yunpei
 */
@Component
public class LlmProviderFactory {

    private final LlmProviderProperties properties;
    private final Object lock = new Object();
    private volatile LlmProvider cachedOpenAi;

    /** 测试便利构造：固定 mock。 */
    public LlmProviderFactory() {
        this(new LlmProviderProperties());
    }

    /** Spring 注入主构造。 */
    @Autowired
    public LlmProviderFactory(LlmProviderProperties properties) {
        this.properties = properties == null ? new LlmProviderProperties() : properties;
    }

    /**
     * 返回当前选定的 Provider。
     *
     * @return LLM Provider
     */
    public LlmProvider provider() {
        if (properties.isOpenAiCompatible()) {
            LlmProvider existing = cachedOpenAi;
            if (existing != null) {
                return existing;
            }
            synchronized (lock) {
                if (cachedOpenAi == null) {
                    cachedOpenAi = new OpenAiCompatibleLlmProvider(properties.getOpenaiCompatible());
                }
                return cachedOpenAi;
            }
        }
        return new MockLlmProvider();
    }
}
