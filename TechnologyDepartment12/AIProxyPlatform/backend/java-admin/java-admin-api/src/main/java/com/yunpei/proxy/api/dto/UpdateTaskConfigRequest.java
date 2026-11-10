package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;

/**
 * 更新任务配置请求。
 *
 * <p>用「字段 setter 是否被调用」区分未传与显式 null（对齐 mock 部分更新语义）。
 * Jackson 仅在 JSON 中出现该 key 时调用 setter；缺省字段不会触发，{@code *Present} 保持 false。
 *
 * @author yunpei
 */
@Getter
@Schema(description = "更新任务配置（字段未出现则不改；出现且为 null 则清空可选字段）")
public class UpdateTaskConfigRequest {

    private String code;

    @JsonIgnore
    private boolean codePresent;

    private String name;

    @JsonIgnore
    private boolean namePresent;

    private String workflowType;

    @JsonIgnore
    private boolean workflowTypePresent;

    private String taskQueue;

    @JsonIgnore
    private boolean taskQueuePresent;

    private String cronExpr;

    @JsonIgnore
    private boolean cronExprPresent;

    private Map<String, Object> retryPolicy;

    @JsonIgnore
    private boolean retryPolicyPresent;

    private Integer timeoutSeconds;

    @JsonIgnore
    private boolean timeoutSecondsPresent;

    private String remark;

    @JsonIgnore
    private boolean remarkPresent;

    private Integer isEnabled;

    @JsonIgnore
    private boolean enabledPresent;

    public void setCode(String code) {
        this.code = code;
        this.codePresent = true;
    }

    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
        this.workflowTypePresent = true;
    }

    public void setTaskQueue(String taskQueue) {
        this.taskQueue = taskQueue;
        this.taskQueuePresent = true;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
        this.cronExprPresent = true;
    }

    public void setRetryPolicy(Map<String, Object> retryPolicy) {
        this.retryPolicy = retryPolicy;
        this.retryPolicyPresent = true;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.timeoutSecondsPresent = true;
    }

    public void setRemark(String remark) {
        this.remark = remark;
        this.remarkPresent = true;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
        this.enabledPresent = true;
    }
}
