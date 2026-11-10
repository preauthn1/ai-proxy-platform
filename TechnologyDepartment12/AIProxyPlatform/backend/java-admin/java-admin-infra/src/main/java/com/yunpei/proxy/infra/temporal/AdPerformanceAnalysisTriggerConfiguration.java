package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.AdPerformanceAnalysisTriggerPort;
import io.temporal.client.WorkflowClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 广告效果分析触发端口装配（Temporal / 本地回退）。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public class AdPerformanceAnalysisTriggerConfiguration {

    private static final String TEMPORAL_ENABLED =
            "${spring.temporal.test-server.enabled:false} || '${spring.temporal.connection.target:}'.length() > 0";

    @Bean
    @ConditionalOnExpression(TEMPORAL_ENABLED)
    public AdPerformanceAnalysisTriggerPort temporalAdPerformanceAnalysisTriggerPort(WorkflowClient workflowClient) {
        return new TemporalAdPerformanceAnalysisTriggerPort(workflowClient);
    }

    @Bean
    @ConditionalOnMissingBean(AdPerformanceAnalysisTriggerPort.class)
    public AdPerformanceAnalysisTriggerPort localAdPerformanceAnalysisTriggerPort() {
        return new LocalAdPerformanceAnalysisTriggerPort();
    }
}
