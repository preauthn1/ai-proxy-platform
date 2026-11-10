package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.VideoGenerationTriggerPort;
import io.temporal.client.WorkflowClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 视频生成触发端口装配。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public class VideoGenerationTriggerConfiguration {

    private static final String TEMPORAL_ENABLED =
            "${spring.temporal.test-server.enabled:false} || '${spring.temporal.connection.target:}'.length() > 0";

    @Bean
    @ConditionalOnExpression(TEMPORAL_ENABLED)
    public VideoGenerationTriggerPort temporalVideoGenerationTriggerPort(WorkflowClient workflowClient) {
        return new TemporalVideoGenerationTriggerPort(workflowClient);
    }

    @Bean
    @ConditionalOnMissingBean(VideoGenerationTriggerPort.class)
    public VideoGenerationTriggerPort localVideoGenerationTriggerPort() {
        return new LocalVideoGenerationTriggerPort();
    }
}
