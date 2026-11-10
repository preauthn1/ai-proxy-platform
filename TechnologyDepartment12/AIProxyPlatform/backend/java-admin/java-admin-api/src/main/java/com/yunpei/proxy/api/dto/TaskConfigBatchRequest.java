package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.task.TaskManageModels.TaskBatchCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 任务配置批量操作。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = TaskBatchCommand.class)
@Schema(description = "任务配置批量 enable|disable|delete|trigger")
public class TaskConfigBatchRequest {

    @Schema(description = "enable | disable | delete | trigger", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
