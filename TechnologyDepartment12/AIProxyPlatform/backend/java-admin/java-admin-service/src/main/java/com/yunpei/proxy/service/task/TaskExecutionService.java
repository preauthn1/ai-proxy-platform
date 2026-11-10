package com.yunpei.proxy.service.task;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionListQuery;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionView;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 任务执行记录 Service：分页/详情（只读；配置名软解析）。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TemporalTaskExecutionRepository executionRepository;
    private final TemporalTaskConfigRepository configRepository;

    public PageData<TaskExecutionView> page(TaskExecutionListQuery query) {
        if (query.status() != null && !TaskManageModels.EXECUTION_STATUSES.contains(query.status())) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "status must be one of " + TaskManageModels.EXECUTION_STATUSES);
        }
        EasyPageResult<TemporalTaskExecution> page = executionRepository.page(
                query.page(),
                query.pageSize(),
                query.configId(),
                query.status(),
                query.startedAtFrom(),
                query.startedAtTo(),
                query.workflowType());
        List<TemporalTaskExecution> rows = page.getData() == null ? List.of() : page.getData();
        Map<Long, String> nameMap = resolveConfigNames(rows);
        // configId 可空（图片/视频直启 Workflow 种子行无 temporal_task_config）；
        // ImmutableMap#get 不允许 null key → 须先判空。
        return PageData.of(
                rows.stream()
                        .map(r -> toView(r, r.getConfigId() == null ? null : nameMap.get(r.getConfigId())))
                        .toList(),
                page.getTotal());
    }

    public TaskExecutionView getById(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        TemporalTaskExecution row = executionRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "task-execution " + id + " not found");
        }
        String configName = null;
        if (row.getConfigId() != null) {
            configName =
                    configRepository.mapNameByIds(List.of(row.getConfigId())).get(row.getConfigId());
        }
        return toView(row, configName);
    }

    private Map<Long, String> resolveConfigNames(List<TemporalTaskExecution> rows) {
        Set<Long> ids = rows.stream()
                .map(TemporalTaskExecution::getConfigId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        return configRepository.mapNameByIds(ids);
    }

    private TaskExecutionView toView(TemporalTaskExecution e, String configName) {
        return new TaskExecutionView(
                e.getId(),
                e.getConfigId(),
                configName,
                e.getWorkflowId(),
                e.getRunId(),
                e.getWorkflowType(),
                e.getTaskQueue(),
                e.getStatus(),
                e.getPendingAt(),
                e.getStartedAt(),
                e.getClosedAt(),
                TaskJsonSupport.parseObject(e.getInputSummary(), "inputSummary"),
                TaskJsonSupport.parseObject(e.getResultSummary(), "resultSummary"),
                e.getFailureReason(),
                e.getRetryCount() == null ? 0 : e.getRetryCount(),
                e.getCreatedAt());
    }
}
