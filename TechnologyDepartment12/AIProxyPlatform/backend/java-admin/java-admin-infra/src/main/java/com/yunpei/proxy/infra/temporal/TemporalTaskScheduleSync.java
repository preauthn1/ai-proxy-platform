package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.port.TaskSchedulePort;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.task.TaskJsonSupport;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleState;
import io.temporal.client.schedules.ScheduleUpdate;
import io.temporal.common.RetryOptions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将 DB {@code temporal_task_config} 幂等同步到 Temporal Schedule。
 *
 * <p>实现 {@link TaskSchedulePort}：后台 CRUD 写库后调用 {@link #apply} 即时同步；
 * 启动时 {@link #syncAll} 做全量对账。
 *
 * <p>规则：
 * <ul>
 *   <li>启用且 cron 非空 → create 或 update Schedule，并确保 unpaused
 *   <li>禁用或无 cron → 若 Schedule 已存在则 pause（DB 为配置源）
 *   <li>全量同步时单条失败记日志，不中断整批；CRUD 单条 {@link #apply} 失败抛业务异常
 * </ul>
 *
 * <p>ScheduleId 固定为 {@code task-{code}}，多实例启动重复 sync 安全。
 *
 * @author yunpei
 */
public class TemporalTaskScheduleSync implements TaskSchedulePort {

    private static final Logger log = LoggerFactory.getLogger(TemporalTaskScheduleSync.class);

    static final String SCHEDULE_ID_PREFIX = "task-";

    /**
     * 秒级节拍 cron（如 0/10 或 star/10 六段、五段）。高频调度改用 Interval，比 cron 更可靠。
     */
    private static final Pattern SECOND_INTERVAL_CRON = Pattern.compile("^(?:0|\\*)/(\\d+)(?:\\s+\\*){4,5}$");

    /** 启动/对账时对瞬时 gRPC 错误的重试次数（含首次）。 */
    private static final int TRANSIENT_MAX_ATTEMPTS = 3;

    private static final long TRANSIENT_BACKOFF_MS = 500L;

    private final ScheduleClient scheduleClient;
    private final TemporalTaskConfigRepository configRepository;

    public TemporalTaskScheduleSync(ScheduleClient scheduleClient, TemporalTaskConfigRepository configRepository) {
        this.scheduleClient = scheduleClient;
        this.configRepository = configRepository;
    }

    /** 从 DB 加载全部活跃配置并同步到 Temporal。 */
    public SyncSummary syncAll() {
        List<TemporalTaskConfig> configs = configRepository.listAllActive();
        int upserted = 0;
        int paused = 0;
        int skipped = 0;
        int failed = 0;

        for (TemporalTaskConfig config : configs) {
            try {
                SyncAction action = syncOneWithRetry(config);
                switch (action) {
                    case UPSERTED -> upserted++;
                    case PAUSED -> paused++;
                    case SKIPPED -> skipped++;
                    default -> {
                        // no-op
                    }
                }
            } catch (RuntimeException ex) {
                failed++;
                log.atWarn()
                        .addKeyValue("code", config.getCode())
                        .addKeyValue("id", config.getId())
                        .addKeyValue("scheduleId", scheduleId(config.getCode()))
                        .addKeyValue("reason", TemporalScheduleErrors.formatReason(ex))
                        .setCause(ex)
                        .log("Temporal schedule sync failed");
            }
        }

        SyncSummary summary = new SyncSummary(configs.size(), upserted, paused, skipped, failed);
        log.atInfo()
                .addKeyValue("total", summary.total())
                .addKeyValue("upserted", summary.upserted())
                .addKeyValue("paused", summary.paused())
                .addKeyValue("skipped", summary.skipped())
                .addKeyValue("failed", summary.failed())
                .log("Temporal schedule sync done");
        return summary;
    }

    /**
     * CRUD 即时同步：失败抛 {@link BizException}，由调用方感知。
     */
    @Override
    public void apply(TemporalTaskConfig config) {
        try {
            SyncAction action = syncOneWithRetry(config);
            log.atInfo()
                    .addKeyValue("code", config == null ? null : config.getCode())
                    .addKeyValue("action", action)
                    .log("Temporal schedule applied from CRUD");
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String code = config == null ? "?" : config.getCode();
            throw BizException.of(
                    ResultCode.REMOTE_CALL_FAILED,
                    "Temporal schedule sync failed for code=" + code + ": " + TemporalScheduleErrors.formatReason(ex));
        }
    }

    /**
     * 同步单条配置；对瞬时 gRPC 错误短暂重试（Temporal 启动竞态 / 网络抖动）。
     */
    SyncAction syncOneWithRetry(TemporalTaskConfig config) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= TRANSIENT_MAX_ATTEMPTS; attempt++) {
            try {
                return syncOne(config);
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt >= TRANSIENT_MAX_ATTEMPTS || !TemporalScheduleErrors.isTransient(ex)) {
                    throw ex;
                }
                log.atWarn()
                        .addKeyValue("code", config == null ? null : config.getCode())
                        .addKeyValue("attempt", attempt)
                        .addKeyValue("maxAttempts", TRANSIENT_MAX_ATTEMPTS)
                        .addKeyValue("reason", TemporalScheduleErrors.formatReason(ex))
                        .log("Temporal schedule sync transient failure, retrying");
                sleepQuietly(TRANSIENT_BACKOFF_MS * attempt);
            }
        }
        throw last != null ? last : new IllegalStateException("syncOneWithRetry exhausted without result");
    }

    /**
     * 同步单条配置。
     *
     * @return 执行动作（供单测断言）
     */
    SyncAction syncOne(TemporalTaskConfig config) {
        if (config == null || config.getCode() == null || config.getCode().isBlank()) {
            return SyncAction.SKIPPED;
        }
        String scheduleId = scheduleId(config.getCode());
        boolean enabled = config.getIsEnabled() != null && config.getIsEnabled() == 1;
        String cron = config.getCronExpr() == null ? null : config.getCronExpr().trim();
        boolean hasCron = cron != null && !cron.isEmpty();

        if (enabled && hasCron) {
            upsertSchedule(scheduleId, config, normalizeCronForTemporal(cron));
            return SyncAction.UPSERTED;
        }

        // 禁用 / 无 cron / 软删：暂停已有 Schedule；不存在则跳过
        if (pauseIfExists(scheduleId, disabledNote(enabled, hasCron))) {
            return SyncAction.PAUSED;
        }
        return SyncAction.SKIPPED;
    }

    static String scheduleId(String code) {
        return SCHEDULE_ID_PREFIX + code.trim();
    }

    /**
     * 将库内 cron（常为 Quartz 风格，含 {@code ?}）规范为 Temporal 可接受的表达式。
     *
     * <p>Temporal Schedule 使用标准 cron（无 Quartz 的 {@code ?}）；将 {@code ?} 替换为 {@code *}。
     */
    static String normalizeCronForTemporal(String cron) {
        if (cron == null) {
            return null;
        }
        return cron.trim().replace('?', '*');
    }

    /**
     * 从秒级 cron 解析 interval；非秒级返回 null，走 cron 表达式。
     */
    static Duration parseSecondInterval(String cron) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        Matcher m = SECOND_INTERVAL_CRON.matcher(cron.trim());
        if (!m.matches()) {
            return null;
        }
        int seconds = Integer.parseInt(m.group(1));
        return seconds > 0 ? Duration.ofSeconds(seconds) : null;
    }

    private void upsertSchedule(String scheduleId, TemporalTaskConfig config, String cron) {
        Schedule schedule = buildSchedule(config, cron, false);
        ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
        Duration interval = parseSecondInterval(cron);
        String cadence = interval != null ? "every " + interval.toSeconds() + "s" : "cron=" + cron;

        if (exists(handle)) {
            handle.update(input -> new ScheduleUpdate(schedule));
            // update 可能保留旧 paused；启用任务显式恢复
            if (isPaused(handle)) {
                handle.unpause("enabled in DB on startup sync");
            }
            log.atInfo()
                    .addKeyValue("scheduleId", scheduleId)
                    .addKeyValue("code", config.getCode())
                    .addKeyValue("cadence", cadence)
                    .addKeyValue("businessType", config.getWorkflowType())
                    .log("Temporal schedule updated");
            return;
        }

        try {
            // 创建后立刻跑一次，避免等下一拍才看到效果
            scheduleClient.createSchedule(
                    scheduleId,
                    schedule,
                    ScheduleOptions.newBuilder().setTriggerImmediately(true).build());
            log.atInfo()
                    .addKeyValue("scheduleId", scheduleId)
                    .addKeyValue("code", config.getCode())
                    .addKeyValue("cadence", cadence)
                    .addKeyValue("businessType", config.getWorkflowType())
                    .log("Temporal schedule created");
        } catch (ScheduleAlreadyRunningException ex) {
            // 并发启动竞态：改 update
            handle.update(input -> new ScheduleUpdate(schedule));
            if (isPaused(handle)) {
                handle.unpause("enabled in DB on startup sync");
            }
            log.atInfo()
                    .addKeyValue("scheduleId", scheduleId)
                    .addKeyValue("code", config.getCode())
                    .log("Temporal schedule created-raced-then-updated");
        }
    }

    private boolean pauseIfExists(String scheduleId, String note) {
        ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
        if (!exists(handle)) {
            return false;
        }
        if (!isPaused(handle)) {
            handle.pause(note);
            log.atInfo()
                    .addKeyValue("scheduleId", scheduleId)
                    .addKeyValue("note", note)
                    .log("Temporal schedule paused");
        }
        return true;
    }

    private Schedule buildSchedule(TemporalTaskConfig config, String cron, boolean paused) {
        String businessWorkflowType = requireNonBlank(config.getWorkflowType(), "workflowType");
        String taskQueue = requireNonBlank(config.getTaskQueue(), "taskQueue");
        String code = config.getCode().trim();

        // Schedule 动作禁止改 WorkflowIdReusePolicy（须保持 SDK 默认）。
        // workflowId 作前缀即可：每次调度由 Temporal 生成唯一 Id，避免固定 Id 挡住后续拍。
        WorkflowOptions.Builder options = WorkflowOptions.newBuilder()
                .setWorkflowId(TemporalTaskTriggerPort.buildScheduleWorkflowIdPrefix(code))
                .setTaskQueue(taskQueue)
                .setMemo(TemporalTaskTriggerPort.buildMemo(config.getId(), code));

        if (config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0) {
            options.setWorkflowExecutionTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        }

        Map<String, Object> retryPolicy = TaskJsonSupport.parseObject(config.getRetryPolicy(), "retryPolicy");
        RetryOptions retryOptions = TemporalTaskTriggerPort.toRetryOptions(retryPolicy);
        if (retryOptions != null) {
            options.setRetryOptions(retryOptions);
        }

        Map<String, Object> businessInput = new LinkedHashMap<>();
        businessInput.put("trigger", "schedule");
        businessInput.put("configCode", code);
        if (config.getId() != null) {
            businessInput.put("configId", config.getId());
        }

        // 直启业务 Workflow；执行记录由 ExecutionMirrorTick 镜像
        ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                .setWorkflowType(businessWorkflowType)
                .setOptions(options.build())
                .setArguments(businessInput)
                .build();

        ScheduleSpec.Builder specBuilder = ScheduleSpec.newBuilder().setTimeZoneName(TimeZones.PLATFORM_ID);
        Duration interval = parseSecondInterval(cron);
        if (interval != null) {
            // 亚分钟节拍用 Interval（如每 10s），比 6 段 cron 更稳
            specBuilder.setIntervals(List.of(new ScheduleIntervalSpec(interval)));
        } else {
            specBuilder.setCronExpressions(List.of(cron));
        }

        ScheduleState state = ScheduleState.newBuilder()
                .setPaused(paused)
                .setNote(paused ? "paused by startup sync" : "synced from temporal_task_config")
                .build();

        return Schedule.newBuilder()
                .setAction(action)
                .setSpec(specBuilder.build())
                .setState(state)
                .build();
    }

    private static boolean exists(ScheduleHandle handle) {
        try {
            handle.describe();
            return true;
        } catch (RuntimeException ex) {
            if (TemporalScheduleErrors.isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private static boolean isPaused(ScheduleHandle handle) {
        try {
            ScheduleState state = handle.describe().getSchedule().getState();
            return state != null && state.isPaused();
        } catch (RuntimeException ex) {
            if (TemporalScheduleErrors.isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    /** @see TemporalScheduleErrors#isNotFound(Throwable) */
    static boolean isNotFound(Throwable error) {
        return TemporalScheduleErrors.isNotFound(error);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String disabledNote(boolean enabled, boolean hasCron) {
        if (!enabled) {
            return "disabled in DB on startup sync";
        }
        if (!hasCron) {
            return "no cron_expr (manual-only) on startup sync";
        }
        return "paused by startup sync";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /** 同步动作（单条）。 */
    enum SyncAction {
        UPSERTED,
        PAUSED,
        SKIPPED
    }

    /**
     * 整批同步汇总。
     *
     * @param total    扫描配置数
     * @param upserted create/update 数
     * @param paused   pause 数
     * @param skipped  跳过数（无对应 Schedule 的禁用/无 cron）
     * @param failed   失败数
     */
    public record SyncSummary(int total, int upserted, int paused, int skipped, int failed) {}
}
