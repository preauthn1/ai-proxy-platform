package com.yunpei.proxy.service.task;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.port.TaskSchedulePort;
import com.yunpei.proxy.service.port.TaskTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerRequest;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskManageModels.CreateTaskConfigCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchCommand;
import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchResult;
import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigListQuery;
import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigView;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionView;
import com.yunpei.proxy.service.task.TaskManageModels.TaskTriggerResult;
import com.yunpei.proxy.service.task.TaskManageModels.UpdateTaskConfigCommand;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务配置 Service：分页/CRUD/软删/batch/手动触发。
 *
 * <p>workflowType / taskQueue 必须登记在 {@link TemporalWorkflowType} /
 * {@link TemporalTaskQueue}。写库后经 {@link TaskSchedulePort} 即时同步 Temporal Schedule。
 * 手动触发走 {@link TaskTriggerPort}。Temporal 为必要依赖。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class TaskConfigService {

    private static final Pattern CODE_RE = Pattern.compile(TaskManageModels.CODE_PATTERN);

    private final TemporalTaskConfigRepository configRepository;
    private final TemporalTaskExecutionRepository executionRepository;
    private final TaskTriggerPort taskTriggerPort;
    private final TaskSchedulePort taskSchedulePort;

    public PageData<TaskConfigView> page(TaskConfigListQuery query) {
        EasyPageResult<TemporalTaskConfig> page = configRepository.page(
                query.page(),
                query.pageSize(),
                query.codeExact(),
                query.codeLike(),
                query.name(),
                query.status(),
                query.workflowType(),
                query.taskQueue());
        List<TemporalTaskConfig> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(rows.stream().map(this::toConfigView).toList(), page.getTotal());
    }

    public TaskConfigView getById(Long id) {
        return toConfigView(requireConfig(id));
    }

    @Transactional
    public TaskConfigView create(CreateTaskConfigCommand cmd) {
        String code = requireValidCode(cmd.code());
        String name = requireNonBlank(cmd.name(), "name");
        if (name.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 128 chars");
        }
        String workflowType = TemporalWorkflowType.requireCode(cmd.workflowType());
        String taskQueue = TemporalTaskQueue.requireCode(cmd.taskQueue());
        if (configRepository.existsByCode(code, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code " + code + " already exists");
        }

        TemporalTaskConfig row = new TemporalTaskConfig();
        row.setCode(code);
        row.setName(name);
        row.setWorkflowType(workflowType);
        row.setTaskQueue(taskQueue);
        row.setCronExpr(normalizeCron(cmd.cronExpr()));
        row.setRetryPolicy(TaskJsonSupport.toJson(cmd.retryPolicy(), "retryPolicy"));
        row.setTimeoutSeconds(normalizeTimeout(cmd.timeoutSeconds(), true));
        row.setRemark(TaskManageModels.nullToEmpty(cmd.remark()).trim());
        row.setIsEnabled(TaskManageModels.normalize01(cmd.isEnabled(), 1));
        configRepository.insert(row);
        TemporalTaskConfig saved = requireConfig(row.getId());
        taskSchedulePort.apply(saved);
        return toConfigView(saved);
    }

    @Transactional
    public TaskConfigView update(UpdateTaskConfigCommand cmd) {
        TemporalTaskConfig row = requireConfig(cmd.id());

        if (cmd.codePresent()) {
            String code = requireValidCode(cmd.code());
            if (configRepository.existsByCode(code, row.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "code " + code + " already exists");
            }
            row.setCode(code);
        }
        if (cmd.namePresent()) {
            String name = cmd.name() == null ? "" : cmd.name().trim();
            if (name.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name cannot be empty");
            }
            if (name.length() > 128) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 128 chars");
            }
            row.setName(name);
        }
        if (cmd.workflowTypePresent()) {
            row.setWorkflowType(TemporalWorkflowType.requireCode(cmd.workflowType()));
        }
        if (cmd.taskQueuePresent()) {
            row.setTaskQueue(TemporalTaskQueue.requireCode(cmd.taskQueue()));
        }
        if (cmd.cronExprPresent()) {
            row.setCronExpr(normalizeCron(cmd.cronExpr()));
        }
        if (cmd.retryPolicyPresent()) {
            row.setRetryPolicy(TaskJsonSupport.toJson(cmd.retryPolicy(), "retryPolicy"));
        }
        if (cmd.timeoutSecondsPresent()) {
            row.setTimeoutSeconds(normalizeTimeout(cmd.timeoutSeconds(), true));
        }
        if (cmd.remarkPresent()) {
            row.setRemark(cmd.remark() == null ? "" : cmd.remark());
        }
        if (cmd.isEnabledPresent()) {
            row.setIsEnabled(TaskManageModels.normalize01(cmd.isEnabled(), 1));
        }

        configRepository.update(row);
        TemporalTaskConfig saved = requireConfig(row.getId());
        taskSchedulePort.apply(saved);
        return toConfigView(saved);
    }

    /**
     * 软删任务配置；允许已有 execution（config_id 可悬空）；并 pause 对应 Schedule。
     */
    @Transactional
    public TaskConfigView softDelete(Long id) {
        TemporalTaskConfig row = requireConfig(id);
        TaskConfigView snapshot = toConfigView(row);
        long n = configRepository.softDeleteById(id);
        if (n == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "task-config " + id + " not found");
        }
        // 软删后按「禁用」语义 pause Schedule
        TemporalTaskConfig paused = copyForSchedule(row);
        paused.setIsEnabled(0);
        taskSchedulePort.apply(paused);
        long deletedAt = System.currentTimeMillis();
        return new TaskConfigView(
                snapshot.id(),
                snapshot.code(),
                snapshot.name(),
                snapshot.workflowType(),
                snapshot.taskQueue(),
                snapshot.cronExpr(),
                snapshot.retryPolicy(),
                snapshot.timeoutSeconds(),
                snapshot.remark(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy());
    }

    @Transactional
    public TaskBatchResult batch(TaskBatchCommand cmd) {
        String action = cmd.action() == null ? "" : cmd.action().trim();
        if (!TaskManageModels.BATCH_ACTIONS.contains(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be enable|disable|delete|trigger");
        }
        List<Long> ids = normalizeIds(cmd.ids());
        if (ids.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids must be a non-empty number[]");
        }
        List<TemporalTaskConfig> targets = configRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active task-config found for given ids");
        }

        if ("delete".equals(action)) {
            List<Long> deleted = new ArrayList<>();
            for (TemporalTaskConfig t : targets) {
                configRepository.softDeleteById(t.getId());
                TemporalTaskConfig paused = copyForSchedule(t);
                paused.setIsEnabled(0);
                taskSchedulePort.apply(paused);
                deleted.add(t.getId());
            }
            return new TaskBatchResult(action, deleted.size(), deleted, List.of(), List.of());
        }

        if ("trigger".equals(action)) {
            List<TemporalTaskConfig> enabled = targets.stream()
                    .filter(t -> t.getIsEnabled() != null && t.getIsEnabled() == 1)
                    .toList();
            if (enabled.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "no enabled task-config to trigger");
            }
            List<Long> executionIds = new ArrayList<>();
            List<Long> triggered = new ArrayList<>();
            for (TemporalTaskConfig t : enabled) {
                TaskExecutionView exec = doTrigger(t);
                if (exec.id() != null) {
                    executionIds.add(exec.id());
                }
                triggered.add(t.getId());
            }
            List<Long> skipped = targets.stream()
                    .filter(t -> t.getIsEnabled() == null || t.getIsEnabled() == 0)
                    .map(TemporalTaskConfig::getId)
                    .toList();
            return new TaskBatchResult(action, triggered.size(), triggered, executionIds, skipped);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (TemporalTaskConfig t : targets) {
            configRepository.updateIsEnabled(t.getId(), enabled);
            TemporalTaskConfig snapshot = copyForSchedule(t);
            snapshot.setIsEnabled(enabled);
            taskSchedulePort.apply(snapshot);
            affected.add(t.getId());
        }
        return new TaskBatchResult(action, affected.size(), affected, List.of(), List.of());
    }

    /**
     * 手动触发；禁用配置拒绝。
     */
    @Transactional
    public TaskTriggerResult trigger(Long id) {
        TemporalTaskConfig config = requireConfig(id);
        if (config.getIsEnabled() == null || config.getIsEnabled() == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "disabled task cannot be triggered");
        }
        TaskConfigView configView = toConfigView(config);
        TaskExecutionView execution = doTrigger(config);
        return new TaskTriggerResult(configView, execution);
    }

    /**
     * 直启业务 Workflow，并立即插入 PENDING 种子行（pendingAt/startedAt 均为空）；
     * 进入等待时间与真正运行开始由 ExecutionMirrorTick 从 Temporal pending_activities 对账写入。
     */
    private TaskExecutionView doTrigger(TemporalTaskConfig config) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("trigger", "manual");
        input.put("configCode", config.getCode());
        if (config.getId() != null) {
            input.put("configId", config.getId());
        }

        TriggerResult started = taskTriggerPort.start(new TriggerRequest(
                config.getId(),
                config.getCode(),
                config.getWorkflowType(),
                config.getTaskQueue(),
                config.getCronExpr(),
                TaskJsonSupport.parseObject(config.getRetryPolicy(), "retryPolicy"),
                config.getTimeoutSeconds(),
                input));

        LocalDateTime now = TimeZones.now();
        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setConfigId(config.getId());
        row.setWorkflowId(started.workflowId());
        row.setRunId(started.runId());
        row.setWorkflowType(config.getWorkflowType());
        row.setTaskQueue(config.getTaskQueue());
        row.setStatus("PENDING");
        row.setPendingAt(null);
        row.setStartedAt(null);
        row.setClosedAt(null);
        row.setInputSummary(TaskJsonSupport.toJson(input, "inputSummary"));
        row.setResultSummary(null);
        row.setFailureReason(null);
        row.setRetryCount(0);
        row.setCreatedAt(now);
        executionRepository.insert(row);

        return new TaskExecutionView(
                row.getId(),
                config.getId(),
                config.getName(),
                started.workflowId(),
                started.runId(),
                config.getWorkflowType(),
                config.getTaskQueue(),
                "PENDING",
                null,
                null,
                null,
                input,
                null,
                null,
                0,
                now);
    }

    private TemporalTaskConfig requireConfig(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        TemporalTaskConfig row = configRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "task-config " + id + " not found");
        }
        return row;
    }

    private String requireValidCode(String raw) {
        String code = requireNonBlank(raw, "code");
        if (!CODE_RE.matcher(code).matches()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code must match ^[a-z][a-z0-9_]{0,63}$");
        }
        return code;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is required");
        }
        return value.trim();
    }

    private static String normalizeCron(String cronExpr) {
        if (cronExpr == null) {
            return null;
        }
        String c = cronExpr.trim();
        if (c.isEmpty()) {
            return null;
        }
        if (c.length() > 64) {
            throw BizException.of(ResultCode.PARAM_INVALID, "cronExpr must be ≤ 64 chars");
        }
        return c;
    }

    private static Integer normalizeTimeout(Integer timeoutSeconds, boolean allowNull) {
        if (timeoutSeconds == null) {
            if (allowNull) {
                return null;
            }
            throw BizException.of(ResultCode.PARAM_INVALID, "timeoutSeconds is required");
        }
        if (timeoutSeconds < 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "timeoutSeconds must be a non-negative integer");
        }
        return timeoutSeconds;
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return List.copyOf(set);
    }

    /**
     * 浅拷贝调度相关字段，避免在内存中改写 repository 缓存对象语义。
     */
    private static TemporalTaskConfig copyForSchedule(TemporalTaskConfig src) {
        TemporalTaskConfig c = new TemporalTaskConfig();
        c.setId(src.getId());
        c.setCode(src.getCode());
        c.setName(src.getName());
        c.setWorkflowType(src.getWorkflowType());
        c.setTaskQueue(src.getTaskQueue());
        c.setCronExpr(src.getCronExpr());
        c.setRetryPolicy(src.getRetryPolicy());
        c.setTimeoutSeconds(src.getTimeoutSeconds());
        c.setIsEnabled(src.getIsEnabled());
        return c;
    }

    private TaskConfigView toConfigView(TemporalTaskConfig t) {
        return new TaskConfigView(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getWorkflowType(),
                t.getTaskQueue(),
                t.getCronExpr(),
                TaskJsonSupport.parseObject(t.getRetryPolicy(), "retryPolicy"),
                t.getTimeoutSeconds(),
                TaskManageModels.nullToEmpty(t.getRemark()),
                t.getIsEnabled(),
                t.getDeletedAt() == null ? 0L : t.getDeletedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getCreatedBy() == null ? 0L : t.getCreatedBy(),
                t.getUpdatedBy() == null ? 0L : t.getUpdatedBy());
    }
}
