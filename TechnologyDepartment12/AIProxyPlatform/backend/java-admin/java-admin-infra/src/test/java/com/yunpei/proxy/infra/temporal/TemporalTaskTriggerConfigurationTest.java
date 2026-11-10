package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.service.port.TaskSchedulePort;
import com.yunpei.proxy.service.port.TaskTriggerPort;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.schedules.ScheduleClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证 Temporal 为必要依赖：缺少 Client 时装配失败；具备 Client 时注册真实 Port。
 */
class TemporalTaskTriggerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TemporalTaskTriggerConfiguration.class)
            .withPropertyValues("spring.main.web-application-type=none");

    @Test
    void withoutTemporalClients_contextFails() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void withTemporalClients_registersTriggerAndSchedulePorts() {
        runner.withUserConfiguration(WorkflowClientFixture.class)
                .withBean(ScheduleClient.class, () -> mock(ScheduleClient.class))
                .withBean(TemporalTaskConfigRepository.class, () -> mock(TemporalTaskConfigRepository.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(TaskTriggerPort.class);
                    assertThat(ctx.getBean(TaskTriggerPort.class)).isInstanceOf(TemporalTaskTriggerPort.class);
                    assertThat(ctx).hasSingleBean(TemporalTaskScheduleSync.class);
                    assertThat(ctx).hasSingleBean(TemporalTaskScheduleSyncRunner.class);
                    assertThat(ctx).hasSingleBean(TaskSchedulePort.class);
                    assertThat(ctx.getBean(TaskSchedulePort.class))
                            .isSameAs(ctx.getBean(TemporalTaskScheduleSync.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class WorkflowClientFixture {
        @Bean
        WorkflowClient workflowClient() {
            WorkflowClient client = mock(WorkflowClient.class);
            when(client.getOptions())
                    .thenReturn(WorkflowClientOptions.newBuilder()
                            .setNamespace("default")
                            .build());
            return client;
        }
    }
}
