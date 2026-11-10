package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任务执行记录 Repository（种子 insert + 镜像 update + 分页/详情查询）。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class TemporalTaskExecutionRepository {

    /** 未终态：镜像 tick 优先 describe 推进。 */
    public static final List<String> OPEN_STATUSES = List.of("PENDING", "RUNNING", "RETRYING");

    private final EasyEntityQuery easyEntityQuery;

    public TemporalTaskExecution findById(Long id) {
        return easyEntityQuery
                .queryable(TemporalTaskExecution.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public TemporalTaskExecution findByWorkflowIdAndRunId(String workflowId, String runId) {
        if (workflowId == null || workflowId.isBlank() || runId == null || runId.isBlank()) {
            return null;
        }
        return easyEntityQuery
                .queryable(TemporalTaskExecution.class)
                .where(t -> {
                    t.workflowId().eq(workflowId.trim());
                    t.runId().eq(runId.trim());
                })
                .firstOrNull();
    }

    /**
     * 未终态记录（供镜像 tick 双轨①）。
     */
    public List<TemporalTaskExecution> listOpen(int limit) {
        int size = limit <= 0 ? 200 : Math.min(limit, 500);
        return easyEntityQuery
                .queryable(TemporalTaskExecution.class)
                .where(t -> t.status().in(OPEN_STATUSES))
                .orderBy(t -> {
                    t.createdAt().asc();
                    t.id().asc();
                })
                .limit(size)
                .toList();
    }

    public EasyPageResult<TemporalTaskExecution> page(
            int page,
            int pageSize,
            Long configId,
            String status,
            LocalDateTime startedAtFrom,
            LocalDateTime startedAtTo,
            String workflowType) {
        return easyEntityQuery
                .queryable(TemporalTaskExecution.class)
                .where(t -> {
                    t.configId().eq(configId != null, configId);
                    t.status().eq(status != null, status);
                    t.startedAt().ge(startedAtFrom != null, startedAtFrom);
                    t.startedAt().le(startedAtTo != null, startedAtTo);
                    t.workflowType().eq(workflowType != null, workflowType);
                })
                .orderBy(t -> {
                    // PENDING 的 startedAt 可能为 null，按创建时间保证「最新派发优先」
                    t.createdAt().desc();
                    t.id().desc();
                })
                .toPageResult(page, pageSize);
    }

    public void insert(TemporalTaskExecution row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    /**
     * 按主键更新镜像字段（状态 / 摘要 / 时间）。
     *
     * <p>{@code inputSummary}/{@code resultSummary} 仅在非 null 时写入，避免把已有摘要覆盖成空。
     *
     * @return 影响行数
     */
    public long updateMirror(
            Long id,
            String status,
            LocalDateTime pendingAt,
            LocalDateTime startedAt,
            LocalDateTime closedAt,
            String resultSummary,
            String failureReason,
            Integer retryCount) {
        return updateMirror(id, status, pendingAt, startedAt, closedAt, resultSummary, failureReason, retryCount, null);
    }

    /**
     * 按主键更新镜像字段（状态 / 输入输出摘要 / 时间）。
     *
     * @return 影响行数
     */
    public long updateMirror(
            Long id,
            String status,
            LocalDateTime pendingAt,
            LocalDateTime startedAt,
            LocalDateTime closedAt,
            String resultSummary,
            String failureReason,
            Integer retryCount,
            String inputSummary) {
        return easyEntityQuery
                .updatable(TemporalTaskExecution.class)
                .setColumns(t -> {
                    t.status().set(status);
                    if (pendingAt != null) {
                        t.pendingAt().set(pendingAt);
                    }
                    if (startedAt != null) {
                        t.startedAt().set(startedAt);
                    }
                    t.closedAt().set(closedAt);
                    if (inputSummary != null) {
                        t.inputSummary().set(inputSummary);
                    }
                    if (resultSummary != null) {
                        t.resultSummary().set(resultSummary);
                    }
                    t.failureReason().set(failureReason);
                    if (retryCount != null) {
                        t.retryCount().set(retryCount);
                    }
                })
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    /**
     * 将执行记录更新为终态（兼容旧调用点；新路径优先 {@link #updateMirror}）。
     *
     * @return 影响行数
     */
    public long complete(Long id, String status, String resultSummary, String failureReason, LocalDateTime closedAt) {
        return updateMirror(id, status, null, null, closedAt, resultSummary, failureReason, null, null);
    }

    /** 是否为未终态 status。 */
    public static boolean isOpenStatus(String status) {
        return status != null && OPEN_STATUSES.contains(status);
    }

    /** 是否为已知未终态集合中的任一项（供测试/调用方）。 */
    public static Collection<String> openStatuses() {
        return OPEN_STATUSES;
    }
}
