package com.yunpei.proxy.service.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 任务配置 / 执行记录领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class TaskManageModels {

    private TaskManageModels() {}

    /** 任务编码：小写字母开头，后续可含数字与下划线，最长 64。 */
    public static final String CODE_PATTERN = "^[a-z][a-z0-9_]{0,63}$";

    public static final Set<String> EXECUTION_STATUSES = Set.of(
            "PENDING",
            "RUNNING",
            "RETRYING",
            "COMPLETED",
            "FAILED",
            "CANCELLED",
            "TERMINATED",
            "TIMED_OUT",
            "CONTINUED_AS_NEW");

    public static final Set<String> BATCH_ACTIONS = Set.of("enable", "disable", "delete", "trigger");

    // ---------- task-config ----------

    public record TaskConfigListQuery(
            int page,
            int pageSize,
            List<String> codeExact,
            String codeLike,
            String name,
            Integer status,
            String workflowType,
            String taskQueue) {

        public static TaskConfigListQuery of(
                Integer page,
                Integer pageSize,
                List<String> code,
                String name,
                Integer status,
                String workflowType,
                String taskQueue) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            CodeFilter filter = parseCodeFilter(code);
            return new TaskConfigListQuery(
                    pageNo,
                    size,
                    filter.exact(),
                    filter.like(),
                    trimToNull(name),
                    status,
                    trimToNull(workflowType),
                    trimToNull(taskQueue));
        }
    }

    public record CreateTaskConfigCommand(
            String code,
            String name,
            String workflowType,
            String taskQueue,
            String cronExpr,
            Map<String, Object> retryPolicy,
            Integer timeoutSeconds,
            String remark,
            Integer isEnabled) {}

    /**
     * 更新命令。带 {@code *Present} 标志对齐 mock「字段是否出现」语义：
     * 未出现 → 不改；出现且值为 null/空 → 清空可选字段。
     */
    public record UpdateTaskConfigCommand(
            Long id,
            String code,
            boolean codePresent,
            String name,
            boolean namePresent,
            String workflowType,
            boolean workflowTypePresent,
            String taskQueue,
            boolean taskQueuePresent,
            String cronExpr,
            boolean cronExprPresent,
            Map<String, Object> retryPolicy,
            boolean retryPolicyPresent,
            Integer timeoutSeconds,
            boolean timeoutSecondsPresent,
            String remark,
            boolean remarkPresent,
            Integer isEnabled,
            boolean isEnabledPresent) {}

    /**
     * 任务配置视图。
     *
     * <p>{@code retryPolicy} 在 Entity 上为 JSON 字符串，与 {@code Map} 类型不兼容，
     * 故不使用 {@code @AutoMapper}，由 Service 手写映射 + {@link TaskJsonSupport}。
     */
    public record TaskConfigView(
            Long id,
            String code,
            String name,
            String workflowType,
            String taskQueue,
            String cronExpr,
            Map<String, Object> retryPolicy,
            Integer timeoutSeconds,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {}

    public record TaskBatchCommand(String action, List<Long> ids) {}

    public record TaskBatchResult(
            String action, int affected, List<Long> ids, List<Long> executionIds, List<Long> skippedDisabled) {}

    public record TaskTriggerResult(TaskConfigView config, TaskExecutionView execution) {}

    // ---------- task-execution ----------

    public record TaskExecutionListQuery(
            int page,
            int pageSize,
            Long configId,
            String status,
            LocalDateTime startedAtFrom,
            LocalDateTime startedAtTo,
            String workflowType) {

        public static TaskExecutionListQuery of(
                Integer page,
                Integer pageSize,
                Long configId,
                String status,
                LocalDateTime startedAtFrom,
                LocalDateTime startedAtTo,
                String workflowType) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            String normalizedStatus = null;
            if (status != null && !status.isBlank()) {
                normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            }
            return new TaskExecutionListQuery(
                    pageNo, size, configId, normalizedStatus, startedAtFrom, startedAtTo, trimToNull(workflowType));
        }
    }

    /**
     * 任务执行视图。
     *
     * <p>含 enrich {@code configName} 与 JSON 摘要字段，手写映射（见 Service）。
     */
    public record TaskExecutionView(
            Long id,
            Long configId,
            String configName,
            String workflowId,
            String runId,
            String workflowType,
            String taskQueue,
            String status,
            /** 进入等待中的时间；可空。 */
            LocalDateTime pendingAt,
            /** 真正运行开始时间；尚未真正运行时为 null。 */
            LocalDateTime startedAt,
            LocalDateTime closedAt,
            Map<String, Object> inputSummary,
            Map<String, Object> resultSummary,
            String failureReason,
            /* 已发生重试次数；首次为 0。 */
            Integer retryCount,
            LocalDateTime createdAt) {}

    // ---------- helpers ----------

    record CodeFilter(List<String> exact, String like) {}

    static CodeFilter parseCodeFilter(List<String> code) {
        if (code == null || code.isEmpty()) {
            return new CodeFilter(null, null);
        }
        List<String> cleaned = new ArrayList<>();
        for (String c : code) {
            if (c != null && !c.isBlank()) {
                cleaned.add(c.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return new CodeFilter(null, null);
        }
        if (cleaned.size() == 1) {
            return new CodeFilter(null, cleaned.get(0));
        }
        return new CodeFilter(List.copyOf(cleaned), null);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static int normalize01(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value != 0 && value != 1) {
            throw com.yunpei.proxy.common.exception.BizException.of(
                    com.yunpei.proxy.common.result.ResultCode.PARAM_INVALID, "isEnabled must be 0 or 1");
        }
        return value;
    }
}
