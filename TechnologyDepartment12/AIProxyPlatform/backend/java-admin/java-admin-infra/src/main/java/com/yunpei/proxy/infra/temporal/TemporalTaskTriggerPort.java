package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.port.TaskTriggerPort;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowServiceException;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Temporal {@link WorkflowClient} 的任务触发实现。
 *
 * <p>直接启动业务 Workflow（不再经 JobDispatch 包装）；Memo 写入 {@code configId}/{@code configCode}
 * 供镜像 tick 挂接。由 {@link TemporalTaskTriggerConfiguration} 在 Temporal 连接属性启用时注册。
 *
 * @author yunpei
 */
public class TemporalTaskTriggerPort implements TaskTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(TemporalTaskTriggerPort.class);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Memo / workflowId 约定字段（与 Schedule / 镜像解析共用）。 */
    public static final String MEMO_CONFIG_ID = "configId";

    public static final String MEMO_CONFIG_CODE = "configCode";

    private final WorkflowClient workflowClient;
    private final AtomicLong seq = new AtomicLong(0);

    public TemporalTaskTriggerPort(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public TriggerResult start(TriggerRequest request) {
        if (request == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "trigger request is required");
        }
        String businessWorkflowType = requireNonBlank(request.workflowType(), "workflowType");
        String taskQueue = requireNonBlank(request.taskQueue(), "taskQueue");
        String workflowId = buildWorkflowId(request.code());

        WorkflowOptions.Builder options =
                WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue(taskQueue);

        if (request.timeoutSeconds() != null && request.timeoutSeconds() > 0) {
            options.setWorkflowExecutionTimeout(Duration.ofSeconds(request.timeoutSeconds()));
        }

        RetryOptions retryOptions = toRetryOptions(request.retryPolicy());
        if (retryOptions != null) {
            options.setRetryOptions(retryOptions);
        }

        options.setMemo(buildMemo(request.configId(), request.code()));

        Map<String, Object> businessInput = request.input() == null ? Map.of() : request.input();

        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(businessWorkflowType, options.build());
            WorkflowExecution execution = stub.start(businessInput);
            log.atInfo()
                    .addKeyValue("type", businessWorkflowType)
                    .addKeyValue("queue", taskQueue)
                    .addKeyValue("workflowId", execution.getWorkflowId())
                    .addKeyValue("runId", execution.getRunId())
                    .log("Temporal workflow started");
            return new TriggerResult(execution.getWorkflowId(), execution.getRunId());
        } catch (WorkflowServiceException ex) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed: " + ex.getMessage());
        }
    }

    /**
     * 手动触发 workflowId：{@code wf-{code}-{yyyyMMddHHmmss}-{seq}}。
     */
    public static String buildManualWorkflowId(String code, long seq, LocalDateTime stamp) {
        String safe = code == null || code.isBlank() ? "task" : code.trim();
        String ts = stamp.format(STAMP);
        return "wf-" + safe + "-" + ts + "-" + seq;
    }

    /**
     * Schedule 动作 workflowId 前缀：{@code sched-{code}}（Temporal 会追加唯一后缀）。
     */
    public static String buildScheduleWorkflowIdPrefix(String code) {
        String safe = code == null || code.isBlank() ? "task" : code.trim();
        return "sched-" + safe;
    }

    /**
     * 从 workflowId 解析 config code（{@code wf-|sched-} 前缀约定）。
     */
    public static String parseConfigCodeFromWorkflowId(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return null;
        }
        String id = workflowId.trim();
        String prefix = null;
        if (id.startsWith("wf-")) {
            prefix = "wf-";
        } else if (id.startsWith("sched-")) {
            prefix = "sched-";
        }
        if (prefix == null) {
            return null;
        }
        String rest = id.substring(prefix.length());
        if (rest.isEmpty()) {
            return null;
        }
        int dash = rest.indexOf('-');
        String code = dash < 0 ? rest : rest.substring(0, dash);
        return code.isBlank() ? null : code;
    }

    public static Map<String, Object> buildMemo(Long configId, String configCode) {
        Map<String, Object> memo = new LinkedHashMap<>();
        if (configId != null) {
            memo.put(MEMO_CONFIG_ID, configId);
        }
        if (configCode != null && !configCode.isBlank()) {
            memo.put(MEMO_CONFIG_CODE, configCode.trim());
        }
        return memo;
    }

    private String buildWorkflowId(String code) {
        long n = seq.incrementAndGet();
        return buildManualWorkflowId(code, n, TimeZones.now());
    }

    /**
     * 将配置侧 JSON 对象映射为 Temporal {@link RetryOptions}（供 trigger / schedule 复用）。
     *
     * <p>兼容 mock/seed 字段：{@code maxAttempts}、{@code initialInterval}（如 {@code 30s}）、
     * {@code backoff}。
     */
    public static RetryOptions toRetryOptions(Map<String, Object> retryPolicy) {
        if (retryPolicy == null || retryPolicy.isEmpty()) {
            return null;
        }
        RetryOptions.Builder builder = RetryOptions.newBuilder();
        boolean any = false;

        Object maxAttempts = firstNonNull(retryPolicy, "maxAttempts", "maximumAttempts");
        if (maxAttempts instanceof Number number && number.intValue() > 0) {
            builder.setMaximumAttempts(number.intValue());
            any = true;
        }

        Object initial = firstNonNull(retryPolicy, "initialInterval", "initial_interval");
        Duration initialInterval = parseDuration(initial);
        if (initialInterval != null && !initialInterval.isZero() && !initialInterval.isNegative()) {
            builder.setInitialInterval(initialInterval);
            any = true;
        }

        Object backoff = firstNonNull(retryPolicy, "backoff", "backoffCoefficient");
        if (backoff instanceof Number number && number.doubleValue() > 0) {
            builder.setBackoffCoefficient(number.doubleValue());
            any = true;
        }

        return any ? builder.build() : null;
    }

    static Duration parseDuration(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            long seconds = number.longValue();
            return seconds <= 0 ? null : Duration.ofSeconds(seconds);
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.endsWith("ms") && text.length() > 2) {
                return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
            }
            if (text.endsWith("s") && text.length() > 1 && !text.endsWith("ms")) {
                char unitPrev = text.charAt(text.length() - 2);
                if (Character.isDigit(unitPrev)) {
                    return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
                }
            }
            if (text.endsWith("m") && text.length() > 1 && Character.isDigit(text.charAt(text.length() - 2))) {
                return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            if (text.endsWith("h") && text.length() > 1 && Character.isDigit(text.charAt(text.length() - 2))) {
                return Duration.ofHours(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            // ISO-8601，如 PT30S
            return Duration.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is required");
        }
        return value.trim();
    }
}
