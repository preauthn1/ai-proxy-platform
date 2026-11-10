package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.TaskTriggerPort;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import io.temporal.client.WorkflowClient;
import io.temporal.client.schedules.ScheduleClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal 任务触发 / 调度端口装配 + 启动时 Schedule 全量对账。
 *
 * <p><b>Temporal 为必要依赖</b>：不再提供本地 mock / no-op 回退。需配置
 * {@code spring.temporal.connection.target}（或 {@code test-server.enabled=true}）以便 starter
 * 创建 {@link WorkflowClient} / {@link ScheduleClient}；否则应用无法启动。
 *
 * <p>{@link ScheduleClient} 由 Temporal starter 自动配置（bean 名 temporalScheduleClient），
 * 勿重复定义同名 Bean。{@link TemporalTaskScheduleSync} 同时作为 {@code TaskSchedulePort} 供
 * CRUD 即时同步；启动 {@link TemporalTaskScheduleSyncRunner} 仅作对账。
 *
 * <p>Worker 优雅停机见 {@link TemporalWorkerGracefulShutdownConfiguration}。
 *
 * @author yunpei
 * @see <a href="https://docs.temporal.io/develop/java/integrations/spring-boot-integration">Spring Boot integration</a>
 */
@Configuration(proxyBeanMethods = false)
public class TemporalTaskTriggerConfiguration {

    /**
     * Temporal Workflow 触发（手动 trigger / batch trigger）。
     */
    @Bean
    public TaskTriggerPort temporalTaskTriggerPort(WorkflowClient workflowClient) {
        return new TemporalTaskTriggerPort(workflowClient);
    }

    /**
     * DB → Temporal Schedule 同步器（实现 TaskSchedulePort）。
     *
     * <p>勿再单独注册同实例的 TaskSchedulePort Bean，否则注入会歧义。
     */
    @Bean
    public TemporalTaskScheduleSync temporalTaskScheduleSync(
            ScheduleClient scheduleClient, TemporalTaskConfigRepository configRepository) {
        return new TemporalTaskScheduleSync(scheduleClient, configRepository);
    }

    /**
     * 启动后执行一次全量对账。
     */
    @Bean
    public TemporalTaskScheduleSyncRunner temporalTaskScheduleSyncRunner(TemporalTaskScheduleSync scheduleSync) {
        return new TemporalTaskScheduleSyncRunner(scheduleSync);
    }
}
