package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateTaskConfigRequest;
import com.yunpei.proxy.api.dto.TaskConfigBatchRequest;
import com.yunpei.proxy.api.dto.UpdateTaskConfigRequest;
import com.yunpei.proxy.api.vo.TaskConfigBatchResultVO;
import com.yunpei.proxy.api.vo.TaskConfigVO;
import com.yunpei.proxy.api.vo.TaskOptionVO;
import com.yunpei.proxy.api.vo.TaskTriggerResultVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.task.TaskConfigService;
import com.yunpei.proxy.service.task.TaskManageModels.CreateTaskConfigCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchResult;
import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigListQuery;
import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigView;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionView;
import com.yunpei.proxy.service.task.TaskManageModels.TaskTriggerResult;
import com.yunpei.proxy.service.task.TaskManageModels.UpdateTaskConfigCommand;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link TaskConfigController} 契约测试。
 */
class TaskConfigControllerTest {

    private final TaskConfigService taskConfigService = mock(TaskConfigService.class);
    private final Converter converter = new Converter();
    private final TaskConfigController controller = new TaskConfigController(taskConfigService, converter);

    @Test
    void list_returnsItemsTotal() {
        when(taskConfigService.page(ArgumentMatchers.any(TaskConfigListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleConfig(1L, "log_count_tick")), 1L));

        Result<PageData<TaskConfigVO>> result = controller.list(1, 20, null, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItems().get(0).getCode()).isEqualTo("log_count_tick");
    }

    @Test
    void workflowTypes_returnsRegisteredOptions() {
        Result<List<TaskOptionVO>> result = controller.workflowTypes();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotEmpty();
        assertThat(result.getData().stream().map(TaskOptionVO::getValue).toList())
                .containsExactlyElementsOf(TemporalWorkflowType.ALL);
        assertThat(result.getData().get(0).getLabel())
                .isEqualTo(result.getData().get(0).getValue());
    }

    @Test
    void taskQueues_returnsRegisteredOptions() {
        Result<List<TaskOptionVO>> result = controller.taskQueues();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotEmpty();
        assertThat(result.getData().stream().map(TaskOptionVO::getValue).toList())
                .containsExactlyElementsOf(TemporalTaskQueue.ALL);
    }

    @Test
    void create_mapsBody() {
        when(taskConfigService.create(ArgumentMatchers.any(CreateTaskConfigCommand.class)))
                .thenReturn(sampleConfig(10L, "cache_warmup"));
        CreateTaskConfigRequest req = new CreateTaskConfigRequest();
        req.setCode("cache_warmup");
        req.setName("缓存预热");
        req.setWorkflowType("CacheWarmupWorkflow");
        req.setTaskQueue("maintenance");

        Result<TaskConfigVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateTaskConfigCommand> cap = ArgumentCaptor.forClass(CreateTaskConfigCommand.class);
        verify(taskConfigService).create(cap.capture());
        assertThat(cap.getValue().code()).isEqualTo("cache_warmup");
        assertThat(cap.getValue().workflowType()).isEqualTo("CacheWarmupWorkflow");
    }

    @Test
    void update_usesFieldPresence() {
        when(taskConfigService.update(ArgumentMatchers.any(UpdateTaskConfigCommand.class)))
                .thenReturn(sampleConfig(2L, "log_count_tick"));
        UpdateTaskConfigRequest body = new UpdateTaskConfigRequest();
        body.setIsEnabled(0);

        Result<TaskConfigVO> result = controller.update(2L, body);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateTaskConfigCommand> cap = ArgumentCaptor.forClass(UpdateTaskConfigCommand.class);
        verify(taskConfigService).update(cap.capture());
        UpdateTaskConfigCommand cmd = cap.getValue();
        assertThat(cmd.id()).isEqualTo(2L);
        assertThat(cmd.isEnabledPresent()).isTrue();
        assertThat(cmd.isEnabled()).isEqualTo(0);
        assertThat(cmd.namePresent()).isFalse();
        assertThat(cmd.cronExprPresent()).isFalse();
    }

    @Test
    void update_canClearCronExprWithNull() {
        when(taskConfigService.update(ArgumentMatchers.any(UpdateTaskConfigCommand.class)))
                .thenReturn(sampleConfig(2L, "log_count_tick"));
        UpdateTaskConfigRequest body = new UpdateTaskConfigRequest();
        body.setCronExpr(null);

        controller.update(2L, body);

        ArgumentCaptor<UpdateTaskConfigCommand> cap = ArgumentCaptor.forClass(UpdateTaskConfigCommand.class);
        verify(taskConfigService).update(cap.capture());
        assertThat(cap.getValue().cronExprPresent()).isTrue();
        assertThat(cap.getValue().cronExpr()).isNull();
    }

    @Test
    void batch_returnsTriggerExtras() {
        when(taskConfigService.batch(ArgumentMatchers.any()))
                .thenReturn(new TaskBatchResult("trigger", 1, List.of(1L), List.of(99L), List.of(2L)));
        TaskConfigBatchRequest req = new TaskConfigBatchRequest();
        req.setAction("trigger");
        req.setIds(List.of(1L, 2L));

        Result<TaskConfigBatchResultVO> result = controller.batch(req);

        assertThat(result.getData().getAffected()).isEqualTo(1);
        assertThat(result.getData().getExecutionIds()).containsExactly(99L);
        assertThat(result.getData().getSkippedDisabled()).containsExactly(2L);
    }

    @Test
    void trigger_returnsConfigAndExecution() {
        when(taskConfigService.trigger(1L))
                .thenReturn(new TaskTriggerResult(sampleConfig(1L, "log_count_tick"), sampleExecution(9L, 1L)));

        Result<TaskTriggerResultVO> result = controller.trigger(1L);

        assertThat(result.getData().getConfig().getCode()).isEqualTo("log_count_tick");
        assertThat(result.getData().getExecution().getId()).isEqualTo(9L);
    }

    private static TaskConfigView sampleConfig(Long id, String code) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new TaskConfigView(
                id,
                code,
                "名称",
                "LogCountTickWorkflow",
                "demo",
                "0 0 2 * * ?",
                Map.of("maxAttempts", 3),
                3600,
                "",
                1,
                0L,
                now,
                now,
                0L,
                0L);
    }

    private static TaskExecutionView sampleExecution(Long id, Long configId) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 2, 0);
        return new TaskExecutionView(
                id,
                configId,
                "名称",
                "wf-report-1",
                "run-1",
                "LogCountTickWorkflow",
                "demo",
                "PENDING",
                now,
                null,
                null,
                Map.of("trigger", "manual"),
                null,
                null,
                0,
                now);
    }
}
