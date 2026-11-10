package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务执行记录 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = TaskExecutionView.class)
@Schema(description = "任务执行记录")
public class TaskExecutionVO {

    private Long id;
    private Long configId;
    private String configName;
    private String workflowId;
    private String runId;
    private String workflowType;
    private String taskQueue;
    private String status;
    /** 进入等待中的时间。 */
    private LocalDateTime pendingAt;
    /** 真正运行开始时间；尚未真正运行时为 null。 */
    private LocalDateTime startedAt;

    private LocalDateTime closedAt;
    private Map<String, Object> inputSummary;
    private Map<String, Object> resultSummary;
    private String failureReason;
    /** 已发生重试次数；首次为 0。 */
    private Integer retryCount;

    private LocalDateTime createdAt;
}
