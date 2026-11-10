package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
import io.temporal.client.WorkflowClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 文本生成触发端口装配（Temporal / 本地回退）。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public class AiGenerationTriggerConfiguration {

    private static final String TEMPORAL_ENABLED =
            "${spring.temporal.test-server.enabled:false} || '${spring.temporal.connection.target:}'.length() > 0";

    @Bean
    @ConditionalOnExpression(TEMPORAL_ENABLED)
    public AiGenerationTriggerPort temporalAiGenerationTriggerPort(WorkflowClient workflowClient) {
        return new TemporalAiGenerationTriggerPort(workflowClient);
    }

    @Bean
    @ConditionalOnMissingBean(AiGenerationTriggerPort.class)
    public AiGenerationTriggerPort localAiGenerationTriggerPort() {
        return new LocalAiGenerationTriggerPort();
    }
}
