package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.vo.TaskExecutionVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.task.TaskExecutionService;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionListQuery;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionView;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务执行记录（路径对齐 mock {@code /api/system/task-execution/*}）。
 *
 * @author yunpei
 */
@Tag(name = "任务执行", description = "分页/详情（只读）")
@RestController
@RequestMapping("/api/system/task-execution")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TaskExecutionController {

    private final TaskExecutionService taskExecutionService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询任务执行", description = "筛选 configId/status/startedAt 区间/workflowType；最新优先")
    public Result<PageData<TaskExecutionVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startedAtTo,
            @RequestParam(required = false) String workflowType) {
        PageData<TaskExecutionView> pageData = taskExecutionService.page(
                TaskExecutionListQuery.of(page, pageSize, configId, status, startedAtFrom, startedAtTo, workflowType));
        List<TaskExecutionVO> items = converter.convert(pageData.getItems(), TaskExecutionVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务执行详情")
    public Result<TaskExecutionVO> detail(@PathVariable Long id) {
        return Result.ok(converter.convert(taskExecutionService.getById(id), TaskExecutionVO.class));
    }
}
