package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.task.TaskManageModels.CreateTaskConfigCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 创建任务配置请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateTaskConfigCommand.class)
@Schema(description = "创建任务配置")
public class CreateTaskConfigRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "任务编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "report_daily")
    private String code;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "任务名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Temporal workflow 类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workflowType;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "task queue", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskQueue;

    @Size(max = 64)
    @Schema(description = "cron 表达式；null/空=仅手动触发")
    private String cronExpr;

    @Schema(description = "重试策略 JSON 对象")
    private Map<String, Object> retryPolicy;

    @Schema(description = "超时秒数")
    private Integer timeoutSeconds;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
