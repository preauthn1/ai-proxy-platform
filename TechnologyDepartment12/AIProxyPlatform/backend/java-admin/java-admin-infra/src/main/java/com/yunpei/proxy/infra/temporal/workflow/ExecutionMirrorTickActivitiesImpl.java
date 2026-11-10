package com.yunpei.proxy.infra.temporal.workflow;

import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.infra.temporal.TemporalTaskTriggerPort;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskJsonSupport;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.api.common.v1.Payloads;
import io.temporal.api.enums.v1.PendingActivityState;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.failure.v1.Failure;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.history.v1.WorkflowExecutionStartedEventAttributes;
import io.temporal.api.workflow.v1.PendingActivityInfo;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowExecutionMetadata;
import io.temporal.client.WorkflowStub;
import io.temporal.common.converter.DataConverter;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ExecutionMirrorTickActivities}：DB 未终态 describe + Visibility 扫新 run。
 *
 * <p>双轨语义：
 * <ul>
 *   <li>① 读库中 PENDING/RUNNING/RETRYING 行，对每条 {@code describe} 推进状态
 *   <li>② 按业务 {@link TemporalWorkflowType#FOR_MIRROR} list Visibility 近 {@link #LOOKBACK} 内 run，
 *       upsert 补建（主要覆盖 Schedule 触发、无种子行；含 ImageGeneration 等直启类型）
 * </ul>
 *
 * <p>摘要：
 * <ul>
 *   <li>{@code result_summary}：终态 {@code getResult}
 *   <li>{@code input_summary}：History 首事件 {@code WorkflowExecutionStarted.input}；
 *       若业务结果 Map 含 {@code input} 键也可兜底回填
 * </ul>
 *
 * <p>开放态 Activity 镜像（describe {@code pending_activities}）：
 * <ul>
 *   <li>Workflow 仍为 RUNNING 时，结合 pending 提升/细化业务 status：
 *       <ul>
 *         <li>{@code RETRYING}：attempt &gt; 1 或已有 {@code last_failure}；
 *             {@code retry_count = max(0, attempt - 1)}
 *         <li>{@code PENDING}（等待中）：存在 pending 且尚未真正执行
 *             （state 均为 SCHEDULED / PAUSED / UNSPECIFIED 等）
 *         <li>{@code RUNNING}：任一 pending 已 STARTED（或取消/暂停请求中）
 *       </ul>
 *   <li>时间字段（优先 Temporal pending_activities，首次写入后不覆盖）：
 *       <ul>
 *         <li>{@code pendingAt} ← Activity {@code scheduled_time}（进入排队/等待）；
 *             无 scheduled 时回退 workflow 启动时间 / now——<b>所有任务都应有值</b>
 *         <li>{@code startedAt} ← Activity {@code last_started_time}（真正开始执行）；
 *             PENDING 时保持 null
 *       </ul>
 *   <li>Visibility list 元数据无 pending：pendingAt 先用 workflow StartTime 占位，
 *       开放行由双轨① describe 用 scheduled_time 精化（库内已有则不覆盖）
 * </ul>
 *
 * <p>Worker 注册队列见 {@link TemporalTaskQueue#SYSTEM}（系统队列，与业务 {@code demo} 隔离）。
 *
 * @author yunpei
 */
@Component
@ActivityImpl(taskQueues = TemporalTaskQueue.SYSTEM)
public class ExecutionMirrorTickActivitiesImpl implements ExecutionMirrorTickActivities {

    private static final Logger log = LoggerFactory.getLogger(ExecutionMirrorTickActivitiesImpl.class);

    /** 单轮最多 describe 的未终态 DB 行数，防止开放任务过多拖垮 tick。 */
    private static final int OPEN_LIMIT = 200;

    /** 每个业务 workflowType 单轮 Visibility 最多消费条数。 */
    private static final int VISIBILITY_LIMIT_PER_TYPE = 100;

    /**
     * Visibility 回看窗口：与 Schedule 间隔重叠，避免漏扫刚结束的 run；
     * 过大会放大 list 成本。
     */
    private static final Duration LOOKBACK = Duration.ofMinutes(15);

    private final WorkflowClient workflowClient;
    private final TemporalTaskExecutionRepository executionRepository;
    private final TemporalTaskConfigRepository configRepository;

    public ExecutionMirrorTickActivitiesImpl(
            WorkflowClient workflowClient,
            TemporalTaskExecutionRepository executionRepository,
            TemporalTaskConfigRepository configRepository) {
        this.workflowClient = workflowClient;
        this.executionRepository = executionRepository;
        this.configRepository = configRepository;
    }

    /**
     * 跑一轮双轨镜像。
     *
     * @return 本轮实际 insert/update 的行数（无变化跳过不计）
     */
    @Override
    public int mirrorOnce() {
        int touched = 0;
        // workflowId|runId，避免 ① 已处理的行在 ② 再 upsert 一遍
        Set<String> seen = new HashSet<>();

        // ① DB 未终态
        List<TemporalTaskExecution> open = executionRepository.listOpen(OPEN_LIMIT);
        for (TemporalTaskExecution row : open) {
            try {
                if (mirrorExisting(row)) {
                    touched++;
                }
                seen.add(key(row.getWorkflowId(), row.getRunId()));
            } catch (RuntimeException ex) {
                log.atWarn()
                        .addKeyValue("id", row.getId())
                        .addKeyValue("workflowId", row.getWorkflowId())
                        .addKeyValue("reason", ex.getMessage())
                        .log("mirror open row failed");
            }
        }

        // ② Visibility：可镜像业务类型近期 run（含直启 ImageGeneration）
        Instant since = Instant.now().minus(LOOKBACK);
        for (String workflowType : TemporalWorkflowType.FOR_MIRROR) {
            String query = "WorkflowType = \"" + workflowType + "\" AND StartTime > \"" + since + "\"";
            try (var stream = workflowClient.listExecutions(query)) {
                int n = 0;
                var it = stream.iterator();
                while (it.hasNext() && n < VISIBILITY_LIMIT_PER_TYPE) {
                    WorkflowExecutionMetadata meta = it.next();
                    n++;
                    if (meta == null || meta.getExecution() == null) {
                        continue;
                    }
                    String wfId = meta.getExecution().getWorkflowId();
                    String runId = meta.getExecution().getRunId();
                    if (shouldSkipWorkflow(wfId, meta.getWorkflowType())) {
                        continue;
                    }
                    String k = key(wfId, runId);
                    if (seen.contains(k)) {
                        continue;
                    }
                    seen.add(k);
                    try {
                        if (mirrorMetadata(meta)) {
                            touched++;
                        }
                    } catch (RuntimeException ex) {
                        log.atWarn()
                                .addKeyValue("workflowId", wfId)
                                .addKeyValue("reason", ex.getMessage())
                                .log("mirror visibility row failed");
                    }
                }
            } catch (RuntimeException ex) {
                log.atWarn()
                        .addKeyValue("type", workflowType)
                        .addKeyValue("reason", ex.getMessage())
                        .log("listExecutions failed");
            }
        }

        log.atInfo()
                .addKeyValue("touched", touched)
                .addKeyValue("scannedKeys", seen.size())
                .log("ExecutionMirrorTick mirrorOnce");
        return touched;
    }

    /**
     * 双轨①：对已有 DB 行 describe Temporal 并写回。
     *
     * @param row 未终态镜像行（须含 workflowId；runId/workflowType 可空）
     * @return 是否发生 DB 写入
     */
    private boolean mirrorExisting(TemporalTaskExecution row) {
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(
                row.getWorkflowId(),
                java.util.Optional.ofNullable(row.getRunId()),
                java.util.Optional.ofNullable(row.getWorkflowType()));
        WorkflowExecutionMetadata meta = stub.describe();
        return applySnapshot(row, meta, true);
    }

    /**
     * 双轨②：Visibility 元数据 → 已有行则更新，否则 insert 种子。
     *
     * @param meta listExecutions 返回的执行摘要（含 status / 时间 / memo）
     * @return 是否发生 DB 写入
     */
    private boolean mirrorMetadata(WorkflowExecutionMetadata meta) {
        String wfId = meta.getExecution().getWorkflowId();
        String runId = meta.getExecution().getRunId();
        TemporalTaskExecution existing = executionRepository.findByWorkflowIdAndRunId(wfId, runId);
        if (existing != null) {
            return applySnapshot(existing, meta, true);
        }
        TemporalTaskExecution created = newSeedFromMeta(meta);
        if (created == null) {
            return false;
        }
        executionRepository.insert(created);
        // list 元数据无 result；终态时再 describe 补 result/failure
        if (isTerminal(mapStatus(meta.getStatus()))) {
            try {
                WorkflowStub stub = workflowClient.newUntypedWorkflowStub(
                        wfId, java.util.Optional.of(runId), java.util.Optional.ofNullable(meta.getWorkflowType()));
                applySnapshot(created, stub.describe(), true);
            } catch (RuntimeException ignored) {
                // 已插入基础行即可
            }
        }
        return true;
    }

    /**
     * 由 Visibility 元数据构造待 insert 的镜像行（尚无主键）。
     *
     * @param meta Temporal 执行摘要
     * @return 新行；异常输入理论上不出现，保留 null 防御
     */
    private TemporalTaskExecution newSeedFromMeta(WorkflowExecutionMetadata meta) {
        String wfId = meta.getExecution().getWorkflowId();
        String runId = meta.getExecution().getRunId();
        Long configId = resolveConfigId(meta, wfId);
        LocalDateTime now = TimeZones.now();
        LocalDateTime temporalStart =
                toLocal(meta.getStartTime() != null ? meta.getStartTime() : meta.getExecutionTime());
        String status = mapStatus(meta.getStatus());
        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setConfigId(configId);
        row.setWorkflowId(wfId);
        row.setRunId(runId);
        row.setWorkflowType(nullToEmpty(meta.getWorkflowType()));
        row.setTaskQueue(nullToEmpty(meta.getTaskQueue()));
        row.setStatus(status);
        // Visibility 无 pending_activities：pendingAt 用 workflow 时间占位（保证非空）；
        // 后续 describe 若库内已有 pendingAt 则不覆盖；有 scheduled_time 且库内为空才精化
        LocalDateTime queueStart = temporalStart != null ? temporalStart : now;
        row.setPendingAt(queueStart);
        if ("PENDING".equals(status)) {
            row.setStartedAt(null);
        } else {
            row.setStartedAt(queueStart);
        }
        row.setClosedAt(toLocal(meta.getCloseTime()));
        // Schedule 触发无 DB 种子行：从 History 拉 Workflow 入参写 input_summary
        row.setInputSummary(tryFetchInputJson(wfId, runId));
        row.setResultSummary(null);
        row.setFailureReason(null);
        row.setRetryCount(0);
        row.setCreatedAt(now);
        return row;
    }

    /**
     * 将 Temporal 快照合并进 DB 行。
     *
     * @param row         已有或刚 insert 的镜像实体（应尽量带 id）
     * @param meta        describe/list 得到的状态与时间
     * @param fetchResult 终态时是否尝试 {@code getResult} 填 result_summary / failure_reason
     * @return true=执行了 updateMirror；false=与库内一致跳过
     */
    private boolean applySnapshot(TemporalTaskExecution row, WorkflowExecutionMetadata meta, boolean fetchResult) {
        String baseStatus = mapStatus(meta.getStatus());
        RetrySnapshot retry = extractRetrySnapshot(meta);
        String status = resolveOpenStatus(baseStatus, retry);
        LocalDateTime now = TimeZones.now();
        LocalDateTime temporalStart =
                toLocal(meta.getExecutionTime() != null ? meta.getExecutionTime() : meta.getStartTime());
        LocalDateTime closedAt = toLocal(meta.getCloseTime());
        // pendingAt / startedAt：优先 pending_activities 的 scheduled_time / last_started_time
        LocalDateTime pendingAt = resolvePendingAt(row, retry, temporalStart, now);
        LocalDateTime startedAt = resolveStartedAt(row, status, retry, temporalStart, now);
        String resultJson = null;
        String failure = null;
        Object rawResult = null;

        if (fetchResult && isTerminal(status)) {
            ResultAndFailure rf = tryFetchResult(row.getWorkflowId(), row.getRunId(), row.getWorkflowType(), status);
            resultJson = rf.resultJson();
            failure = rf.failureReason();
            rawResult = rf.rawResult();
        } else if (!isTerminal(status) && !isBlank(retry.lastFailureMessage())) {
            // 重试中展示最近一次 Activity 失败原因，便于 UI 观察
            failure = retry.lastFailureMessage();
        }

        String inputJson = null;
        if (isBlank(row.getInputSummary())) {
            inputJson = tryFetchInputJson(row.getWorkflowId(), row.getRunId());
            if (inputJson == null) {
                inputJson = extractInputFromResult(rawResult);
            }
        }

        Integer retryCount = retry.retryCount();
        // 无变化则跳过写库
        if (status.equals(row.getStatus())
                && sameTime(row.getClosedAt(), closedAt)
                && sameTime(row.getPendingAt(), pendingAt)
                && sameTime(row.getStartedAt(), startedAt)
                && (resultJson == null || resultJson.equals(row.getResultSummary()))
                && (inputJson == null || inputJson.equals(row.getInputSummary()))
                && (failure == null || failure.equals(row.getFailureReason()))
                && (retryCount == null || Objects.equals(retryCount, row.getRetryCount()))) {
            return false;
        }

        if (row.getId() == null) {
            // insert 后未刷 id 的边角：按 wf+run 再查
            TemporalTaskExecution reloaded =
                    executionRepository.findByWorkflowIdAndRunId(row.getWorkflowId(), row.getRunId());
            if (reloaded == null) {
                return false;
            }
            row.setId(reloaded.getId());
            // 重载后可能已有 input（手动触发种子行）
            if (isBlank(inputJson) && !isBlank(reloaded.getInputSummary())) {
                inputJson = null;
            }
            // 重载后以库内时间为准再解析，避免丢失种子 pendingAt
            pendingAt = resolvePendingAt(reloaded, retry, temporalStart, now);
            startedAt = resolveStartedAt(reloaded, status, retry, temporalStart, now);
        }

        executionRepository.updateMirror(
                row.getId(), status, pendingAt, startedAt, closedAt, resultJson, failure, retryCount, inputJson);
        row.setStatus(status);
        if (pendingAt != null) {
            row.setPendingAt(pendingAt);
        }
        if (startedAt != null) {
            row.setStartedAt(startedAt);
        }
        row.setClosedAt(closedAt);
        if (inputJson != null) {
            row.setInputSummary(inputJson);
        }
        if (resultJson != null) {
            row.setResultSummary(resultJson);
        }
        if (retryCount != null) {
            row.setRetryCount(retryCount);
        }
        row.setFailureReason(failure);
        return true;
    }

    /**
     * 进入等待时间：库内已有值不覆盖；否则优先 Temporal Activity {@code scheduled_time}；
     * 再退到 workflow 启动时间 / now。
     *
     * <p>所有任务都应有 pendingAt。无细粒度 scheduled_time 时与 startedAt 可能同源（排队时长为 0），
     * 仍优于 UI 空白；开放态后续 tick 若仍无库内值且 describe 带回 scheduled_time 可精化。
     */
    static LocalDateTime resolvePendingAt(
            TemporalTaskExecution row, RetrySnapshot retry, LocalDateTime temporalStart, LocalDateTime now) {
        if (row.getPendingAt() != null) {
            return row.getPendingAt();
        }
        LocalDateTime scheduled = retry != null ? retry.earliestScheduledAt() : null;
        if (scheduled != null) {
            return scheduled;
        }
        return temporalStart != null ? temporalStart : now;
    }

    /**
     * 真正运行开始：库内已有值不覆盖；PENDING 保持 null；
     * 否则优先 Temporal Activity {@code last_started_time}，再退到 workflow 时间 / now。
     */
    static LocalDateTime resolveStartedAt(
            TemporalTaskExecution row,
            String status,
            RetrySnapshot retry,
            LocalDateTime temporalStart,
            LocalDateTime now) {
        if (row.getStartedAt() != null) {
            return row.getStartedAt();
        }
        if ("PENDING".equals(status)) {
            return null;
        }
        LocalDateTime lastStarted = retry != null ? retry.earliestLastStartedAt() : null;
        if (lastStarted != null) {
            return lastStarted;
        }
        return temporalStart != null ? temporalStart : now;
    }

    /**
     * 终态尽力取业务结果；失败时抽错误信息。
     *
     * @param workflowId   Temporal workflowId
     * @param runId        Temporal runId（可空则取最新 run）
     * @param workflowType 类型名，供 untyped stub 可选携带
     * @param status       已映射的业务 status（COMPLETED/FAILED/…）
     * @return resultJson 与 failureReason，均可为 null
     */
    private ResultAndFailure tryFetchResult(String workflowId, String runId, String workflowType, String status) {
        if (!"COMPLETED".equals(status) && !"FAILED".equals(status) && !"TIMED_OUT".equals(status)) {
            return new ResultAndFailure(null, null, null);
        }
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(
                    workflowId, java.util.Optional.ofNullable(runId), java.util.Optional.ofNullable(workflowType));
            // 已关闭的执行 getResult 应立即返回；2s 仅防异常挂起
            Object result = stub.getResult(2, TimeUnit.SECONDS, Object.class);
            return new ResultAndFailure(toResultJson(result), null, result);
        } catch (Exception ex) {
            String msg = rootMessage(ex);
            if ("COMPLETED".equals(status)) {
                // void 结果或无法反序列化：不记 failure
                return new ResultAndFailure(null, null, null);
            }
            return new ResultAndFailure(null, blankToNull(msg), null);
        }
    }

    /**
     * 从 History 读取 Workflow 启动入参，序列化为 input_summary JSON。
     *
     * @param workflowId Temporal workflowId
     * @param runId      Temporal runId（可空则最新 run）
     * @return JSON 文本；无入参或失败时 null
     */
    private String tryFetchInputJson(String workflowId, String runId) {
        if (workflowId == null || workflowId.isBlank()) {
            return null;
        }
        String effectiveRunId = runId == null || runId.isBlank() ? null : runId;
        try (Stream<HistoryEvent> stream = workflowClient.streamHistory(workflowId, effectiveRunId)) {
            Optional<HistoryEvent> started = stream.filter(HistoryEvent::hasWorkflowExecutionStartedEventAttributes)
                    .findFirst();
            if (started.isEmpty()) {
                return null;
            }
            return decodeStartedInput(started.get().getWorkflowExecutionStartedEventAttributes(), dataConverter());
        } catch (Exception ex) {
            log.atDebug()
                    .addKeyValue("workflowId", workflowId)
                    .addKeyValue("runId", effectiveRunId)
                    .addKeyValue("reason", ex.getMessage())
                    .log("fetch workflow input failed");
            return null;
        }
    }

    /**
     * 解码 WorkflowExecutionStarted.input → 摘要 JSON。
     *
     * @param attrs     启动事件属性
     * @param converter Temporal DataConverter
     * @return JSON；无 payload 时 null
     */
    static String decodeStartedInput(WorkflowExecutionStartedEventAttributes attrs, DataConverter converter) {
        if (attrs == null || !attrs.hasInput() || converter == null) {
            return null;
        }
        Payloads payloads = attrs.getInput();
        if (payloads.getPayloadsCount() == 0) {
            return null;
        }
        try {
            Object decoded = converter.fromPayloads(0, Optional.of(payloads), Object.class, Object.class);
            return toSummaryJson(decoded);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 业务结果 Map 若含 {@code input} 键，可作 input_summary 兜底。
     *
     * @param result getResult 反序列化对象
     * @return JSON；无法提取时 null
     */
    static String extractInputFromResult(Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            return null;
        }
        Object input = map.get("input");
        if (input == null) {
            return null;
        }
        return toSummaryJson(input);
    }

    private DataConverter dataConverter() {
        return workflowClient.getOptions().getDataConverter();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 解析 config_id：优先 Memo.configId，其次 Memo.configCode / workflowId 约定反查配置表。
     *
     * @param meta       可含 memo
     * @param workflowId 用于 {@code wf-|sched-{code}-...} 解析
     * @return 配置主键；无法解析时 null（允许软悬空）
     */
    private Long resolveConfigId(WorkflowExecutionMetadata meta, String workflowId) {
        Long fromMemo = toLong(safeMemo(meta, TemporalTaskTriggerPort.MEMO_CONFIG_ID));
        if (fromMemo != null) {
            return fromMemo;
        }
        Object memoCode = safeMemo(meta, TemporalTaskTriggerPort.MEMO_CONFIG_CODE);
        String code = memoCode instanceof String s && !s.isBlank()
                ? s.trim()
                : TemporalTaskTriggerPort.parseConfigCodeFromWorkflowId(workflowId);
        if (code == null) {
            return null;
        }
        TemporalTaskConfig config = configRepository.findByCode(code);
        return config == null ? null : config.getId();
    }

    /**
     * 安全读取 Memo 字段。
     *
     * @param meta 执行元数据
     * @param key  Memo 键（如 configId / configCode）
     * @return 解码后的值；键不存在或解码失败时 null
     */
    private static Object safeMemo(WorkflowExecutionMetadata meta, String key) {
        try {
            // SDK 两参 getMemo 返回 Object
            return meta.getMemo(key, Object.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Temporal 原生状态 → 镜像表 status 字面量。
     *
     * @param status Temporal enum；null 视为 RUNNING
     */
    static String mapStatus(WorkflowExecutionStatus status) {
        if (status == null) {
            return "RUNNING";
        }
        return switch (status) {
            case WORKFLOW_EXECUTION_STATUS_COMPLETED -> "COMPLETED";
            case WORKFLOW_EXECUTION_STATUS_FAILED -> "FAILED";
            case WORKFLOW_EXECUTION_STATUS_CANCELED -> "CANCELLED";
            case WORKFLOW_EXECUTION_STATUS_TERMINATED -> "TERMINATED";
            case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> "TIMED_OUT";
            case WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> "CONTINUED_AS_NEW";
            case WORKFLOW_EXECUTION_STATUS_RUNNING, WORKFLOW_EXECUTION_STATUS_PAUSED -> "RUNNING";
            default -> "RUNNING";
        };
    }

    /**
     * 在 Workflow 仍为 RUNNING 时，结合 Activity pending 信息细化开放态。
     *
     * <p>优先序：{@code RETRYING} &gt; {@code PENDING}（仅等待领取/暂停）&gt; 保持 {@code baseStatus}。
     *
     * @param baseStatus {@link #mapStatus} 结果
     * @param retry      describe 提取的 pending 快照；未知时不改 status
     */
    static String resolveOpenStatus(String baseStatus, RetrySnapshot retry) {
        if (retry == null || !retry.known()) {
            return baseStatus;
        }
        if (!"RUNNING".equals(baseStatus)) {
            return baseStatus;
        }
        if (retry.activityRetrying()) {
            return "RETRYING";
        }
        if (retry.activityWaiting()) {
            return "PENDING";
        }
        return baseStatus;
    }

    /**
     * 从 describe/list 元数据提取 Activity pending 快照。
     *
     * <p>仅 {@link WorkflowExecutionDescription}（stub.describe）带 pending_activities；
     * Visibility list 的 {@link WorkflowExecutionMetadata} 视为未知（不覆盖库内 retry_count / 细粒度状态）。
     */
    static RetrySnapshot extractRetrySnapshot(WorkflowExecutionMetadata meta) {
        if (!(meta instanceof WorkflowExecutionDescription description)) {
            return RetrySnapshot.unknown();
        }
        List<PendingActivityInfo> pending;
        try {
            pending = description.getRawDescription().getPendingActivitiesList();
        } catch (RuntimeException ex) {
            return RetrySnapshot.unknown();
        }
        if (pending.isEmpty()) {
            // describe 已知无 pending：非 Activity 等待/重试中；retry_count 不主动清零（保留历史峰值）
            return RetrySnapshot.notRetrying();
        }
        int maxAttempt = 0;
        String lastFailure = null;
        boolean anyStarted = false;
        // scheduled_time = 进入排队；last_started_time = Worker 真正开跑（多 Activity 取最早）
        LocalDateTime earliestScheduled = null;
        LocalDateTime earliestLastStarted = null;
        for (PendingActivityInfo info : pending) {
            if (info == null) {
                continue;
            }
            maxAttempt = Math.max(maxAttempt, info.getAttempt());
            if (info.hasLastFailure()) {
                String msg = failureMessage(info.getLastFailure());
                if (!isBlank(msg)) {
                    lastFailure = msg;
                }
            }
            if (isActivityStartedState(info.getState())) {
                anyStarted = true;
            }
            if (info.hasScheduledTime()) {
                earliestScheduled = minTime(earliestScheduled, toLocal(info.getScheduledTime()));
            }
            if (info.hasLastStartedTime()) {
                earliestLastStarted = minTime(earliestLastStarted, toLocal(info.getLastStartedTime()));
            }
        }
        int retryCount = retryCountFromAttempt(maxAttempt);
        // attempt>1：已进入第 N 次执行；attempt=1 但已有 last_failure：首败后等待下一次
        boolean retrying = maxAttempt > 1 || !isBlank(lastFailure);
        // 仅 SCHEDULED/PAUSED 等、且非重试 → 业务「等待中」
        boolean waiting = !retrying && !anyStarted;
        return new RetrySnapshot(
                true, retrying, waiting, retryCount, lastFailure, earliestScheduled, earliestLastStarted);
    }

    /**
     * pending Activity 是否已真正在 Worker 上执行（相对「排队等待领取」）。
     *
     * @param state Temporal {@link PendingActivityState}；null/未识别视为未启动
     */
    static boolean isActivityStartedState(PendingActivityState state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case PENDING_ACTIVITY_STATE_STARTED,
                    PENDING_ACTIVITY_STATE_CANCEL_REQUESTED,
                    PENDING_ACTIVITY_STATE_PAUSE_REQUESTED -> true;
            case PENDING_ACTIVITY_STATE_SCHEDULED,
                    PENDING_ACTIVITY_STATE_PAUSED,
                    PENDING_ACTIVITY_STATE_UNSPECIFIED,
                    UNRECOGNIZED -> false;
        };
    }

    /**
     * Temporal Activity attempt（从 1 起）→ 镜像 retry_count（首次执行为 0）。
     *
     * @param attempt pending activity 的 attempt；≤0 视为 0
     */
    static int retryCountFromAttempt(int attempt) {
        return Math.max(0, attempt - 1);
    }

    /** Failure.message，空则 null。 */
    static String failureMessage(Failure failure) {
        if (failure == null) {
            return null;
        }
        // protobuf getter 不返回 null，空串视为无消息
        String msg = failure.getMessage().trim();
        return msg.isEmpty() ? null : msg;
    }

    /**
     * 是否为镜像表终态（非 PENDING/RUNNING/RETRYING）。
     *
     * @param status 镜像 status 字符串
     */
    static boolean isTerminal(String status) {
        return status != null && !TemporalTaskExecutionRepository.isOpenStatus(status) && !"PENDING".equals(status);
    }

    /**
     * 是否跳过镜像（系统 tick、遗留 JobDispatch、sys- 前缀）。
     *
     * @param workflowId   Temporal workflowId
     * @param workflowType Temporal workflow type 名
     * @return true=不入库 / 不更新
     */
    static boolean shouldSkipWorkflow(String workflowId, String workflowType) {
        if (workflowType != null
                && (workflowType.equals(TemporalWorkflowType.EXECUTION_MIRROR_TICK)
                        || workflowType.equals("JobDispatchWorkflow"))) {
            return true;
        }
        if (workflowId == null) {
            return true;
        }
        return workflowId.startsWith("sys-");
    }

    /**
     * 业务返回值 → result_summary JSON。
     *
     * @param result getResult 反序列化对象；Map 原样序列化，其它包 {@code {"value":...}}
     */
    static String toResultJson(Object result) {
        return toSummaryJson(result);
    }

    /**
     * 业务值 → 摘要 JSON（input/result 共用）。
     *
     * @param value 反序列化对象；Map 原样序列化，其它包 {@code {"value":...}}
     */
    static String toSummaryJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return TaskJsonSupport.toJson(value, "summary");
        }
        return TaskJsonSupport.toJson(Map.of("value", String.valueOf(value)), "summary");
    }

    /** @param instant Temporal Instant；null → null */
    private static LocalDateTime toLocal(Instant instant) {
        return TimeZones.toLocal(instant);
    }

    /**
     * protobuf Timestamp → 本地时间；缺省/零值视为 null。
     *
     * @param ts {@link com.google.protobuf.Timestamp}；null 或 epoch0 → null
     */
    static LocalDateTime toLocal(com.google.protobuf.Timestamp ts) {
        if (ts == null || (ts.getSeconds() == 0 && ts.getNanos() == 0)) {
            return null;
        }
        return TimeZones.toLocal(Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()));
    }

    /** 取较早非 null 时间。 */
    static LocalDateTime minTime(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    /**
     * @param a 库内 closedAt
     * @param b Temporal closeTime
     */
    private static boolean sameTime(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * 本轮去重键。
     *
     * @param workflowId Temporal workflowId
     * @param runId      Temporal runId
     */
    private static String key(String workflowId, String runId) {
        return nullToEmpty(workflowId) + "|" + nullToEmpty(runId);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** Memo / 数字字段宽松转 Long。 */
    private static Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 抽取最内层可读错误信息（含 ApplicationFailure.originalMessage）。 */
    private static String rootMessage(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable cursor = error;
        while (cursor.getCause() != null && !cursor.getCause().equals(cursor)) {
            cursor = cursor.getCause();
        }
        if (cursor instanceof ApplicationFailure app && app.getOriginalMessage() != null) {
            return app.getOriginalMessage();
        }
        String msg = cursor.getMessage();
        return msg == null || msg.isBlank() ? error.toString() : msg;
    }

    /** 空白视为无失败原因。 */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * getResult 尽力结果。
     *
     * @param resultJson    写入 result_summary 的 JSON；成功且无结果时可为 null
     * @param failureReason 写入 failure_reason；成功时 null
     * @param rawResult     原始业务结果，供从 result.input 兜底 input_summary
     */
    private record ResultAndFailure(String resultJson, String failureReason, Object rawResult) {}

    /**
     * Activity pending 快照（来自 describe pending_activities）。
     *
     * @param known                  是否来自 describe（false=Visibility list 等未知源）
     * @param activityRetrying       是否判定为 Activity 重试中
     * @param activityWaiting        是否仅排队等待（SCHEDULED/PAUSED 等，且非重试）
     * @param retryCount             写入 retry_count；unknown 时为 null（不覆盖库内值）
     * @param lastFailureMessage     pending last_failure.message；无则 null
     * @param earliestScheduledAt    各 pending Activity {@code scheduled_time} 最早值（进入等待）
     * @param earliestLastStartedAt  各 pending Activity {@code last_started_time} 最早值（真正开跑）
     */
    record RetrySnapshot(
            boolean known,
            boolean activityRetrying,
            boolean activityWaiting,
            Integer retryCount,
            String lastFailureMessage,
            LocalDateTime earliestScheduledAt,
            LocalDateTime earliestLastStartedAt) {

        static RetrySnapshot unknown() {
            return new RetrySnapshot(false, false, false, null, null, null, null);
        }

        /** describe 已知无 pending：非等待/重试，且不改 retry_count。 */
        static RetrySnapshot notRetrying() {
            return new RetrySnapshot(true, false, false, null, null, null, null);
        }
    }
}
