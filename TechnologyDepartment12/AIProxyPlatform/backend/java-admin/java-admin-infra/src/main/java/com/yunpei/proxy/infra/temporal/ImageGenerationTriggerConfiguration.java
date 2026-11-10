package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
import io.temporal.client.WorkflowClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片生成触发端口装配（Temporal / 本地回退）。
 *
 * <p>条件与 {@link TemporalTaskTriggerConfiguration} 对齐：{@code spring.temporal.test-server.enabled=true}
 * 或 {@code spring.temporal.connection.target} 非空时注册 {@link TemporalImageGenerationTriggerPort}；
 * 否则回退 {@link LocalImageGenerationTriggerPort}。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public class ImageGenerationTriggerConfiguration {

    private static final String TEMPORAL_ENABLED =
            "${spring.temporal.test-server.enabled:false} || '${spring.temporal.connection.target:}'.length() > 0";

    /**
     * Temporal 启用时注册真实 workflow 触发器。
     */
    @Bean
    @ConditionalOnExpression(TEMPORAL_ENABLED)
    public ImageGenerationTriggerPort temporalImageGenerationTriggerPort(WorkflowClient workflowClient) {
        return new TemporalImageGenerationTriggerPort(workflowClient);
    }

    /**
     * Temporal 未启用时回退本地空实现。
     */
    @Bean
    @ConditionalOnMissingBean(ImageGenerationTriggerPort.class)
    public ImageGenerationTriggerPort localImageGenerationTriggerPort() {
        return new LocalImageGenerationTriggerPort();
    }
}
