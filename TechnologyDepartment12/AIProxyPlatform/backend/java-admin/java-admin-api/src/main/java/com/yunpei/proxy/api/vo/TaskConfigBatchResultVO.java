package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务配置批量操作结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = TaskBatchResult.class)
@Schema(description = "任务配置批量操作结果")
public class TaskConfigBatchResultVO {

    @Schema(description = "动作：enable|disable|delete|trigger")
    private String action;

    @Schema(description = "影响条数")
    private Integer affected;

    @Schema(description = "涉及的配置 ID")
    private List<Long> ids;

    @Schema(description = "trigger 时新建的执行 ID")
    private List<Long> executionIds;

    @Schema(description = "trigger 时跳过的禁用配置 ID")
    private List<Long> skippedDisabled;
}
