package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.infra.temporal.TemporalTaskScheduleSync.SyncAction;
import com.yunpei.proxy.infra.temporal.TemporalTaskScheduleSync.SyncSummary;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleException;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link TemporalTaskScheduleSync} 单测：create / update / pause / skip / 部分失败。
 */
class TemporalTaskScheduleSyncTest {

    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private final ScheduleHandle handle = mock(ScheduleHandle.class);
    private TemporalTaskScheduleSync sync;

    @BeforeEach
    void setUp() {
        sync = new TemporalTaskScheduleSync(scheduleClient, configRepository);
        when(scheduleClient.getHandle(anyString())).thenReturn(handle);
    }

    @Test
    void scheduleId_usesTaskPrefix() {
        assertThat(TemporalTaskScheduleSync.scheduleId("log_count_tick")).isEqualTo("task-log_count_tick");
    }

    @Test
    void apply_enabledWithCron_upserts() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        notFoundOnDescribe();

        sync.apply(config);

        verify(scheduleClient)
                .createSchedule(eq("task-log_count_tick"), any(Schedule.class), any(ScheduleOptions.class));
    }

    @Test
    void normalizeCron_replacesQuartzQuestionMark() {
        assertThat(TemporalTaskScheduleSync.normalizeCronForTemporal("0/10 * * * * ?"))
                .isEqualTo("0/10 * * * * *");
        assertThat(TemporalTaskScheduleSync.normalizeCronForTemporal("0 0 2 * * ?"))
                .isEqualTo("0 0 2 * * *");
    }

    @Test
    void parseSecondInterval_fromHighFrequencyCron() {
        assertThat(TemporalTaskScheduleSync.parseSecondInterval("0/10 * * * * *"))
                .isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(TemporalTaskScheduleSync.parseSecondInterval("*/10 * * * * *"))
                .isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(TemporalTaskScheduleSync.parseSecondInterval("0 0 2 * * *")).isNull();
    }

    @Test
    void syncOne_enabledWithSecondCron_usesIntervalSpec() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0/10 * * * * ?");
        config.setWorkflowType("LogCountTickWorkflow");
        config.setTaskQueue("demo");
        notFoundOnDescribe();

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.UPSERTED);

        ArgumentCaptor<Schedule> scheduleCap = ArgumentCaptor.forClass(Schedule.class);
        ArgumentCaptor<ScheduleOptions> optionsCap = ArgumentCaptor.forClass(ScheduleOptions.class);
        verify(scheduleClient).createSchedule(eq("task-log_count_tick"), scheduleCap.capture(), optionsCap.capture());
        assertThat(scheduleCap.getValue().getSpec().getIntervals()).isNotNull().hasSize(1);
        assertThat(scheduleCap.getValue().getSpec().getIntervals().get(0).getEvery())
                .isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(scheduleCap.getValue().getSpec().getCronExpressions()).isNullOrEmpty();
        assertThat(scheduleCap.getValue().getSpec().getTimeZoneName()).isEqualTo("Asia/Shanghai");
        assertThat(optionsCap.getValue().isTriggerImmediately()).isTrue();
        assertThat(scheduleCap.getValue().getAction()).isInstanceOf(ScheduleActionStartWorkflow.class);
        ScheduleActionStartWorkflow action =
                (ScheduleActionStartWorkflow) scheduleCap.getValue().getAction();
        assertThat(action.getOptions().getWorkflowId()).isEqualTo("sched-log_count_tick");
        assertThat(action.getOptions().getTaskQueue()).isEqualTo("demo");
    }

    @Test
    void syncOne_enabledWithCron_createsWhenMissing() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        notFoundOnDescribe();

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.UPSERTED);

        ArgumentCaptor<Schedule> scheduleCap = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleClient)
                .createSchedule(eq("task-log_count_tick"), scheduleCap.capture(), any(ScheduleOptions.class));
        Schedule schedule = scheduleCap.getValue();
        assertThat(schedule.getSpec().getCronExpressions()).containsExactly("0 0 2 * * *");
        assertThat(schedule.getSpec().getTimeZoneName()).isEqualTo("Asia/Shanghai");
        assertThat(schedule.getAction()).isInstanceOf(ScheduleActionStartWorkflow.class);
        ScheduleActionStartWorkflow action = (ScheduleActionStartWorkflow) schedule.getAction();
        assertThat(action.getWorkflowType()).isEqualTo("LogCountTickWorkflow");
        assertThat(action.getOptions().getTaskQueue()).isEqualTo("demo");
        assertThat(action.getOptions().getWorkflowId()).isEqualTo("sched-log_count_tick");
        assertThat(action.getOptions().getMemo()).containsEntry("configCode", "log_count_tick");
        assertThat(action.getArguments()).isNotNull();
        assertThat(schedule.getSpec().getIntervals()).isNullOrEmpty();
        verify(handle, never()).update(any());
    }

    @Test
    void syncOne_enabledWithCron_updatesAndUnpausesWhenExists() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        // 避免 nested when：先构造返回值再 stub
        ScheduleDescription paused = description(true);
        when(handle.describe()).thenReturn(paused);

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.UPSERTED);

        verify(handle).update(any());
        verify(handle).unpause("enabled in DB on startup sync");
        verify(scheduleClient, never()).createSchedule(anyString(), any(), any());
    }

    @Test
    void syncOne_disabled_pausesExisting() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        config.setIsEnabled(0);
        ScheduleDescription active = description(false);
        when(handle.describe()).thenReturn(active);

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.PAUSED);

        verify(handle).pause("disabled in DB on startup sync");
        verify(scheduleClient, never()).createSchedule(anyString(), any(), any());
    }

    @Test
    void syncOne_disabled_skipsWhenScheduleMissing() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        config.setIsEnabled(0);
        notFoundOnDescribe();

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.SKIPPED);
        verify(handle, never()).pause(anyString());
    }

    @Test
    void syncOne_noCron_skipsWhenMissing() {
        TemporalTaskConfig config = enabledConfig("manual_only", null);
        notFoundOnDescribe();

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.SKIPPED);
        verify(scheduleClient, never()).createSchedule(anyString(), any(), any());
    }

    @Test
    void syncAll_continuesWhenOneFails() {
        TemporalTaskConfig ok = enabledConfig("a", "0 0 1 * * ?");
        TemporalTaskConfig bad = enabledConfig("b", "0 0 2 * * ?");
        when(configRepository.listAllActive()).thenReturn(List.of(ok, bad));

        ScheduleHandle okHandle = mock(ScheduleHandle.class);
        ScheduleHandle badHandle = mock(ScheduleHandle.class);
        when(scheduleClient.getHandle("task-a")).thenReturn(okHandle);
        when(scheduleClient.getHandle("task-b")).thenReturn(badHandle);
        when(okHandle.describe()).thenThrow(notFound());
        when(badHandle.describe()).thenThrow(notFound());
        doThrow(new RuntimeException("boom"))
                .when(scheduleClient)
                .createSchedule(eq("task-b"), any(Schedule.class), any(ScheduleOptions.class));

        SyncSummary summary = sync.syncAll();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.upserted()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        verify(scheduleClient).createSchedule(eq("task-a"), any(Schedule.class), any(ScheduleOptions.class));
    }

    @Test
    void isNotFound_detectsGrpcStatus() {
        assertThat(TemporalTaskScheduleSync.isNotFound(notFound())).isTrue();
        assertThat(TemporalTaskScheduleSync.isNotFound(new RuntimeException("other")))
                .isFalse();
    }

    @Test
    void isNotFound_detectsScheduleExceptionWrappingNotFound() {
        // SDK 真实形态：ScheduleException("Schedule exception", StatusRuntimeException NOT_FOUND)
        ScheduleException wrapped = new ScheduleException(notFound());
        assertThat(TemporalTaskScheduleSync.isNotFound(wrapped)).isTrue();
        assertThat(TemporalScheduleErrors.formatReason(wrapped))
                .contains("Schedule exception")
                .contains("NOT_FOUND");
    }

    @Test
    void syncOne_enabledWithCron_createsWhenDescribeThrowsWrappedNotFound() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        when(handle.describe()).thenThrow(new ScheduleException(notFound()));

        assertThat(sync.syncOne(config)).isEqualTo(SyncAction.UPSERTED);

        verify(scheduleClient)
                .createSchedule(eq("task-log_count_tick"), any(Schedule.class), any(ScheduleOptions.class));
    }

    @Test
    void syncOneWithRetry_retriesTransientThenSucceeds() {
        TemporalTaskConfig config = enabledConfig("log_count_tick", "0 0 2 * * ?");
        StatusRuntimeException unavailable =
                new StatusRuntimeException(Status.UNAVAILABLE.withDescription("connection refused"));
        // 第 1 次瞬时失败，第 2 次 NOT_FOUND → create
        when(handle.describe())
                .thenThrow(new ScheduleException(unavailable))
                .thenThrow(new ScheduleException(notFound()));

        assertThat(sync.syncOneWithRetry(config)).isEqualTo(SyncAction.UPSERTED);
        verify(scheduleClient)
                .createSchedule(eq("task-log_count_tick"), any(Schedule.class), any(ScheduleOptions.class));
    }

    private void notFoundOnDescribe() {
        when(handle.describe()).thenThrow(notFound());
    }

    private static StatusRuntimeException notFound() {
        // 与 Temporal Server 实际文案对齐：workflow not found for ID: temporal-sys-scheduler:...
        return new StatusRuntimeException(
                Status.NOT_FOUND.withDescription("workflow not found for ID: temporal-sys-scheduler:task-missing"));
    }

    private static ScheduleDescription description(boolean paused) {
        ScheduleState state = ScheduleState.newBuilder().setPaused(paused).build();
        Schedule schedule = Schedule.newBuilder()
                .setState(state)
                .setSpec(io.temporal.client.schedules.ScheduleSpec.newBuilder()
                        .setCronExpressions(List.of("0 0 * * *"))
                        .build())
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType("Wf")
                        .setOptions(io.temporal.client.WorkflowOptions.newBuilder()
                                .setTaskQueue("q")
                                .setWorkflowId("id")
                                .build())
                        .build())
                .build();
        ScheduleDescription desc = mock(ScheduleDescription.class);
        when(desc.getSchedule()).thenReturn(schedule);
        return desc;
    }

    private static TemporalTaskConfig enabledConfig(String code, String cron) {
        TemporalTaskConfig c = new TemporalTaskConfig();
        c.setId(1L);
        c.setCode(code);
        c.setName(code);
        c.setWorkflowType("LogCountTickWorkflow");
        c.setTaskQueue("demo");
        c.setCronExpr(cron);
        c.setRetryPolicy("{\"maxAttempts\":2}");
        c.setTimeoutSeconds(3600);
        c.setIsEnabled(1);
        return c;
    }
}
