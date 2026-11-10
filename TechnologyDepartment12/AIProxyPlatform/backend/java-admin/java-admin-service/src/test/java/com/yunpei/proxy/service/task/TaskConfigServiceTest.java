package com.yunpei.proxy.service.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.port.TaskSchedulePort;
import com.yunpei.proxy.service.port.TaskTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskManageModels.CreateTaskConfigCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchResult;
import com.yunpei.proxy.service.task.TaskManageModels.TaskTriggerResult;
import com.yunpei.proxy.service.task.TaskManageModels.UpdateTaskConfigCommand;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link TaskConfigService} 校验、枚举门禁、调度同步与触发逻辑单测。
 */
class TaskConfigServiceTest {

    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private final TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
    private final TaskTriggerPort taskTriggerPort = mock(TaskTriggerPort.class);
    private final TaskSchedulePort taskSchedulePort = mock(TaskSchedulePort.class);
    private TaskConfigService service;

    @BeforeEach
    void setUp() {
        service = new TaskConfigService(configRepository, executionRepository, taskTriggerPort, taskSchedulePort);
        doAnswer(invocation -> {
                    TemporalTaskExecution row = invocation.getArgument(0);
                    if (row.getId() == null) {
                        row.setId(99L);
                    }
                    return null;
                })
                .when(executionRepository)
                .insert(any(TemporalTaskExecution.class));
    }

    @Test
    void create_rejectsInvalidCode() {
        CreateTaskConfigCommand cmd =
                new CreateTaskConfigCommand("Bad-Code", "n", "LogCountTickWorkflow", "demo", null, null, null, null, 1);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("code must match");
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void create_rejectsUnknownWorkflowType() {
        CreateTaskConfigCommand cmd = new CreateTaskConfigCommand(
                "log_count_tick", "日志计数", "UnknownWorkflow", "demo", null, null, null, null, 1);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
        verify(configRepository, never()).insert(any());
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void create_rejectsUnknownTaskQueue() {
        CreateTaskConfigCommand cmd = new CreateTaskConfigCommand(
                "log_count_tick", "日志计数", "LogCountTickWorkflow", "unknown_queue", null, null, null, null, 1);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown taskQueue");
        verify(configRepository, never()).insert(any());
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void create_rejectsSystemTaskQueue() {
        // SYSTEM 仅供系统 Schedule，不进任务配置门禁（对齐 TemporalWorkflowType 系统类型）
        CreateTaskConfigCommand cmd = new CreateTaskConfigCommand(
                "log_count_tick", "日志计数", "LogCountTickWorkflow", TemporalTaskQueue.SYSTEM, null, null, null, null, 1);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown taskQueue");
        verify(configRepository, never()).insert(any());
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(configRepository.existsByCode("log_count_tick", null)).thenReturn(true);
        CreateTaskConfigCommand cmd = new CreateTaskConfigCommand(
                "log_count_tick", "日志计数", "LogCountTickWorkflow", "demo", null, null, null, null, 1);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void create_persistsAndAppliesSchedule() {
        when(configRepository.existsByCode("log_count_tick", null)).thenReturn(false);
        doAnswer(inv -> {
                    TemporalTaskConfig c = inv.getArgument(0);
                    c.setId(10L);
                    return null;
                })
                .when(configRepository)
                .insert(any(TemporalTaskConfig.class));
        when(configRepository.findById(10L)).thenReturn(activeConfig(10L, "log_count_tick", 1));

        CreateTaskConfigCommand cmd = new CreateTaskConfigCommand(
                "log_count_tick", "日志计数", "LogCountTickWorkflow", "demo", "0 0 2 * * ?", null, 3600, null, 1);

        var view = service.create(cmd);

        assertThat(view.code()).isEqualTo("log_count_tick");
        assertThat(view.workflowType()).isEqualTo("LogCountTickWorkflow");
        assertThat(view.taskQueue()).isEqualTo("demo");
        verify(configRepository).insert(any(TemporalTaskConfig.class));
        verify(taskSchedulePort).apply(any(TemporalTaskConfig.class));
    }

    @Test
    void update_partialIsEnabledOnly_appliesSchedule() {
        TemporalTaskConfig row = activeConfig(1L, "log_count_tick", 1);
        when(configRepository.findById(1L)).thenReturn(row);
        when(configRepository.update(row)).thenReturn(1L);

        UpdateTaskConfigCommand cmd = new UpdateTaskConfigCommand(
                1L, null, false, null, false, null, false, null, false, null, false, null, false, null, false, null,
                false, 0, true);

        service.update(cmd);

        assertThat(row.getIsEnabled()).isEqualTo(0);
        verify(configRepository).update(row);
        verify(taskSchedulePort).apply(any(TemporalTaskConfig.class));
    }

    @Test
    void update_rejectsUnknownWorkflowType() {
        TemporalTaskConfig row = activeConfig(1L, "log_count_tick", 1);
        when(configRepository.findById(1L)).thenReturn(row);

        UpdateTaskConfigCommand cmd = new UpdateTaskConfigCommand(
                1L,
                null,
                false,
                null,
                false,
                "NotAWorkflow",
                true,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false);

        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
        verify(configRepository, never()).update(any());
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void trigger_rejectsDisabled() {
        when(configRepository.findById(1L)).thenReturn(activeConfig(1L, "log_count_tick", 0));

        assertThatThrownBy(() -> service.trigger(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("disabled");
        verify(taskTriggerPort, never()).start(any());
    }

    @Test
    void trigger_startsBusinessWorkflow_andInsertsPendingSeed() {
        TemporalTaskConfig config = activeConfig(1L, "log_count_tick", 1);
        when(configRepository.findById(1L)).thenReturn(config);
        when(taskTriggerPort.start(any())).thenReturn(new TriggerResult("wf-1", "run-1"));

        TaskTriggerResult result = service.trigger(1L);

        assertThat(result.config().code()).isEqualTo("log_count_tick");
        assertThat(result.execution().status()).isEqualTo("PENDING");
        // 种子行不写 pendingAt：由 ExecutionMirrorTick 用 Activity scheduled_time 补齐
        assertThat(result.execution().pendingAt()).isNull();
        assertThat(result.execution().startedAt()).isNull();
        assertThat(result.execution().id()).isEqualTo(99L);
        assertThat(result.execution().workflowId()).isEqualTo("wf-1");
        assertThat(result.execution().runId()).isEqualTo("run-1");
        assertThat(result.execution().inputSummary()).containsEntry("trigger", "manual");
        verify(taskTriggerPort).start(any());
        ArgumentCaptor<TemporalTaskExecution> rowCap = ArgumentCaptor.forClass(TemporalTaskExecution.class);
        verify(executionRepository).insert(rowCap.capture());
        TemporalTaskExecution inserted = rowCap.getValue();
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getPendingAt()).isNull();
        assertThat(inserted.getStartedAt()).isNull();
        verify(taskSchedulePort, never()).apply(any());
    }

    @Test
    void batch_triggerSkipsDisabled() {
        TemporalTaskConfig enabled = activeConfig(1L, "a", 1);
        TemporalTaskConfig disabled = activeConfig(2L, "b", 0);
        when(configRepository.listByIds(List.of(1L, 2L))).thenReturn(List.of(enabled, disabled));
        when(configRepository.findById(1L)).thenReturn(enabled);
        when(taskTriggerPort.start(any())).thenReturn(new TriggerResult("wf-x", "run-x"));

        TaskBatchResult result = service.batch(new TaskBatchCommand("trigger", List.of(1L, 2L)));

        assertThat(result.affected()).isEqualTo(1);
        assertThat(result.ids()).containsExactly(1L);
        assertThat(result.skippedDisabled()).containsExactly(2L);
        assertThat(result.executionIds()).containsExactly(99L);
        verify(taskTriggerPort).start(any());
        verify(executionRepository).insert(any(TemporalTaskExecution.class));
    }

    @Test
    void batch_disable_appliesSchedule() {
        TemporalTaskConfig t = activeConfig(1L, "log_count_tick", 1);
        when(configRepository.listByIds(List.of(1L))).thenReturn(List.of(t));

        TaskBatchResult result = service.batch(new TaskBatchCommand("disable", List.of(1L)));

        assertThat(result.affected()).isEqualTo(1);
        verify(configRepository).updateIsEnabled(1L, 0);
        ArgumentCaptor<TemporalTaskConfig> cap = ArgumentCaptor.forClass(TemporalTaskConfig.class);
        verify(taskSchedulePort).apply(cap.capture());
        assertThat(cap.getValue().getIsEnabled()).isEqualTo(0);
    }

    @Test
    void softDelete_allowsExistingExecutions_andPausesSchedule() {
        when(configRepository.findById(3L)).thenReturn(activeConfig(3L, "log_count_tick", 1));
        when(configRepository.softDeleteById(3L)).thenReturn(1L);

        var view = service.softDelete(3L);

        assertThat(view.deletedAt()).isGreaterThan(0L);
        verify(configRepository).softDeleteById(eq(3L));
        ArgumentCaptor<TemporalTaskConfig> cap = ArgumentCaptor.forClass(TemporalTaskConfig.class);
        verify(taskSchedulePort).apply(cap.capture());
        assertThat(cap.getValue().getIsEnabled()).isEqualTo(0);
        assertThat(cap.getValue().getCode()).isEqualTo("log_count_tick");
    }

    private static TemporalTaskConfig activeConfig(Long id, String code, int enabled) {
        TemporalTaskConfig c = new TemporalTaskConfig();
        c.setId(id);
        c.setCode(code);
        c.setName("日志计数");
        c.setWorkflowType("LogCountTickWorkflow");
        c.setTaskQueue("demo");
        c.setCronExpr("0 0 2 * * ?");
        c.setRetryPolicy("{\"maxAttempts\":3}");
        c.setTimeoutSeconds(3600);
        c.setRemark("");
        c.setIsEnabled(enabled);
        c.setDeletedAt(0L);
        return c;
    }
}
