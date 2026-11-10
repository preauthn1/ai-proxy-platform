package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.task.TaskManageModels.TaskConfigView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务配置 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = TaskConfigView.class)
@Schema(description = "任务配置")
public class TaskConfigVO {

    private Long id;
    private String code;
    private String name;
    private String workflowType;
    private String taskQueue;
    private String cronExpr;
    private Map<String, Object> retryPolicy;
    private Integer timeoutSeconds;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
