package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.CreateTaskConfigRequest;
import com.yunpei.proxy.api.dto.TaskConfigBatchRequest;
import com.yunpei.proxy.api.dto.UpdateTaskConfigRequest;
import com.yunpei.proxy.api.vo.TaskConfigBatchResultVO;
import com.yunpei.proxy.api.vo.TaskConfigVO;
import com.yunpei.proxy.api.vo.TaskExecutionVO;
import com.yunpei.proxy.api.vo.TaskOptionVO;
import com.yunpei.proxy.api.vo.TaskTriggerResultVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.task.TaskConfigService;
import com.yunpei.proxy.service.task.TaskManageModels.CreateTaskConfigCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchResult;
import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigListQuery;
import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigView;
import com.yunpei.proxy.service.task.TaskManageModels.TaskTriggerResult;
import com.yunpei.proxy.service.task.TaskManageModels.UpdateTaskConfigCommand;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务配置管理（路径对齐 mock {@code /api/system/task-config/*}）。
 *
 * @author yunpei
 */
@Tag(name = "任务配置", description = "分页/CRUD/软删/batch/手动触发")
@RestController
@RequestMapping("/api/system/task-config")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TaskConfigController {

    private final TaskConfigService taskConfigService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询任务配置", description = "筛选 code/name/status/workflowType/taskQueue；软删不出现")
    public Result<PageData<TaskConfigVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) List<String> code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String taskQueue) {
        PageData<TaskConfigView> pageData = taskConfigService.page(
                TaskConfigListQuery.of(page, pageSize, code, name, status, workflowType, taskQueue));
        List<TaskConfigVO> items = converter.convert(pageData.getItems(), TaskConfigVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @GetMapping("/workflow-types")
    @Operation(summary = "工作流类型下拉选项", description = "来自已注册 TemporalWorkflowType")
    public Result<List<TaskOptionVO>> workflowTypes() {
        List<TaskOptionVO> options = TemporalWorkflowType.ALL.stream()
                .map(v -> new TaskOptionVO(v, v))
                .toList();
        return Result.ok(options);
    }

    @GetMapping("/task-queues")
    @Operation(summary = "任务队列下拉选项", description = "来自已注册 TemporalTaskQueue")
    public Result<List<TaskOptionVO>> taskQueues() {
        List<TaskOptionVO> options =
                TemporalTaskQueue.ALL.stream().map(v -> new TaskOptionVO(v, v)).toList();
        return Result.ok(options);
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务配置详情")
    public Result<TaskConfigVO> detail(@PathVariable Long id) {
        return Result.ok(converter.convert(taskConfigService.getById(id), TaskConfigVO.class));
    }

    @PostMapping
    @Operation(summary = "创建任务配置")
    public Result<TaskConfigVO> create(@Valid @RequestBody CreateTaskConfigRequest req) {
        CreateTaskConfigCommand cmd = converter.convert(req, CreateTaskConfigCommand.class);
        return Result.ok(converter.convert(taskConfigService.create(cmd), TaskConfigVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新任务配置", description = "按 JSON 字段是否出现决定是否修改（对齐 mock）")
    public Result<TaskConfigVO> update(
            @PathVariable Long id, @RequestBody(required = false) UpdateTaskConfigRequest body) {
        UpdateTaskConfigRequest req = body == null ? new UpdateTaskConfigRequest() : body;
        UpdateTaskConfigCommand cmd = toUpdateCommand(id, req);
        return Result.ok(converter.convert(taskConfigService.update(cmd), TaskConfigVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删任务配置", description = "允许存在 execution")
    public Result<TaskConfigVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(taskConfigService.softDelete(id), TaskConfigVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete|trigger")
    public Result<TaskConfigBatchResultVO> batch(@RequestBody TaskConfigBatchRequest req) {
        TaskBatchResult result = taskConfigService.batch(converter.convert(req, TaskBatchCommand.class));
        return Result.ok(converter.convert(result, TaskConfigBatchResultVO.class));
    }

    @PostMapping("/{id}/trigger")
    @Operation(summary = "手动触发任务配置")
    public Result<TaskTriggerResultVO> trigger(@PathVariable Long id) {
        TaskTriggerResult result = taskConfigService.trigger(id);
        return Result.ok(new TaskTriggerResultVO(
                converter.convert(result.config(), TaskConfigVO.class),
                converter.convert(result.execution(), TaskExecutionVO.class)));
    }

    private static UpdateTaskConfigCommand toUpdateCommand(Long id, UpdateTaskConfigRequest req) {
        return new UpdateTaskConfigCommand(
                id,
                req.getCode(),
                req.isCodePresent(),
                req.getName(),
                req.isNamePresent(),
                req.getWorkflowType(),
                req.isWorkflowTypePresent(),
                req.getTaskQueue(),
                req.isTaskQueuePresent(),
                req.getCronExpr(),
                req.isCronExprPresent(),
                req.getRetryPolicy(),
                req.isRetryPolicyPresent(),
                req.getTimeoutSeconds(),
                req.isTimeoutSecondsPresent(),
                req.getRemark(),
                req.isRemarkPresent(),
                req.getIsEnabled(),
                req.isEnabledPresent());
    }
}
