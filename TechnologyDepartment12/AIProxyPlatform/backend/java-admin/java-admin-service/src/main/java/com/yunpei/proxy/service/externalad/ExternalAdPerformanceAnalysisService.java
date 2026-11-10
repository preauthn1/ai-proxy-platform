package com.yunpei.proxy.service.externalad;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.port.AdPerformanceAnalysisTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskJsonSupport;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部广告效果分析：校验、幂等、落库 queued、派发 Temporal、GET 轮询。
 *
 * <p>本波 {@link #execute(String)} 只推进到 processing，不写完整分析报告。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class ExternalAdPerformanceAnalysisService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_DISPATCH_ERROR = 2000;

    private final GenerationTaskRepository repository;
    private final AdPerformanceAnalysisTriggerPort triggerPort;
    private final TemporalTaskExecutionRepository executionRepository;
    private final TemporalTaskConfigRepository configRepository;
    private final AdAnalysisProperties properties;

    /**
     * 创建分析任务：校验 → hash 幂等 → 落库 queued → 派发。
     */
    public CreateAdAnalysisResult createJob(Map<String, Object> rawPayload) {
        Map<String, Object> payload = rawPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawPayload);
        AdAnalysisRequestValidator.validate(payload);
        String externalRequestId = String.valueOf(payload.get("external_request_id"));
        String payloadHash = AdAnalysisPayloadCanonicalizer.payloadHash(payload);

        GenerationTask existing = repository.findByExternalRequestIdAndType(
                externalRequestId, AdAnalysisTaskTypes.TASK_TYPE, AdAnalysisTaskTypes.BUSINESS_TYPE);
        if (existing != null) {
            Map<String, Object> metadata = parseObject(existing.getMetadataJson());
            if (!payloadHash.equals(metadata.get("payload_hash"))) {
                throw new AdAnalysisIdempotencyConflict();
            }
            maybeRetryDispatch(existing, metadata);
            GenerationTask latest = repository.findById(existing.getId());
            return toCreateResult(latest == null ? existing : latest, true);
        }

        String analysisId = newAnalysisId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        GenerationTask row = new GenerationTask();
        row.setId(analysisId);
        row.setTaskType(AdAnalysisTaskTypes.TASK_TYPE);
        row.setBusinessType(AdAnalysisTaskTypes.BUSINESS_TYPE);
        row.setBusinessId(externalRequestId);
        row.setQueueName(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS);
        row.setStatus(AdAnalysisTaskTypes.STATUS_QUEUED);
        row.setPayloadJson(writeObject(payload));
        row.setMetadataJson(writeObject(initialMetadata(externalRequestId, payloadHash)));
        row.setAttemptCount(0);
        row.setMaxAttempts(2);
        row.setCreatedAt(now);
        repository.insert(row);
        dispatchOrFail(row, parseObject(row.getMetadataJson()));
        GenerationTask persisted = repository.findById(analysisId);
        return toCreateResult(persisted == null ? row : persisted, false);
    }

    /**
     * 按 analysis_id 查询；未找到或非分析任务 → {@link AdAnalysisNotFoundException}。
     */
    public AdAnalysisJobView getJob(String analysisId) {
        GenerationTask task = repository.findById(analysisId);
        if (task == null || !AdAnalysisTaskTypes.isAdAnalysisTask(task.getTaskType(), task.getBusinessType())) {
            throw new AdAnalysisNotFoundException();
        }
        return toJobView(task);
    }

    /**
     * Temporal Activity 入口：queued → processing（stage=rules, progress=10）。
     */
    public Map<String, Object> execute(String analysisId) {
        GenerationTask task = repository.findById(analysisId);
        if (task == null || !AdAnalysisTaskTypes.isAdAnalysisTask(task.getTaskType(), task.getBusinessType())) {
            throw new AdAnalysisNotFoundException();
        }
        String status = externalStatus(task.getStatus());
        if (AdAnalysisTaskTypes.STATUS_SUCCEEDED.equals(status) || AdAnalysisTaskTypes.STATUS_FAILED.equals(status)) {
            return Map.of("analysis_id", task.getId(), "status", status);
        }
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        metadata.put("stage", "rules");
        metadata.put("progress", 10);
        if (metadata.get("started_at") == null) {
            metadata.put("started_at", iso(LocalDateTime.now(ZoneOffset.UTC)));
        }
        repository.updateStatus(task.getId(), AdAnalysisTaskTypes.STATUS_PROCESSING);
        repository.updateMetadataJson(task.getId(), writeObject(metadata));
        return Map.of("analysis_id", task.getId(), "status", AdAnalysisTaskTypes.STATUS_PROCESSING, "stage", "rules");
    }

    private void maybeRetryDispatch(GenerationTask existing, Map<String, Object> metadata) {
        if (!AdAnalysisTaskTypes.STATUS_QUEUED.equals(externalStatus(existing.getStatus()))) {
            return;
        }
        boolean dispatched = metadata.get("last_dispatched_at") != null;
        boolean hasError = metadata.get("dispatch_error") != null
                && !String.valueOf(metadata.get("dispatch_error")).isBlank();
        if (dispatched && !hasError) {
            return;
        }
        dispatchOrFail(existing, metadata);
    }

    private void dispatchOrFail(GenerationTask row, Map<String, Object> metadata) {
        try {
            TriggerResult started = triggerPort.start(row.getId());
            if (started == null) {
                return;
            }
            recordDispatchSuccess(row.getId(), metadata);
            insertExecutionSeed(row.getId(), started);
        } catch (RuntimeException ex) {
            recordDispatchFailure(row.getId(), metadata, ex);
            throw new AdAnalysisDispatchException(row.getId(), row.getBusinessId(), ex);
        }
    }

    private void recordDispatchSuccess(String analysisId, Map<String, Object> metadata) {
        int attempts = intValue(metadata.get("dispatch_attempts"), 0) + 1;
        metadata.put("dispatch_attempts", attempts);
        metadata.put("last_dispatched_at", iso(LocalDateTime.now(ZoneOffset.UTC)));
        metadata.remove("dispatch_error");
        repository.updateMetadataJson(analysisId, writeObject(metadata));
    }

    private void recordDispatchFailure(String analysisId, Map<String, Object> metadata, Exception ex) {
        int attempts = intValue(metadata.get("dispatch_attempts"), 0) + 1;
        metadata.put("dispatch_attempts", attempts);
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage().strip();
        if (message.length() > MAX_DISPATCH_ERROR) {
            message = message.substring(0, MAX_DISPATCH_ERROR);
        }
        metadata.put("dispatch_error", message);
        repository.updateMetadataJson(analysisId, writeObject(metadata));
    }

    private void insertExecutionSeed(String analysisId, TriggerResult started) {
        LocalDateTime now = TimeZones.now();
        TemporalTaskConfig config = configRepository.findByCode(AdPerformanceAnalysisTriggerPort.CONFIG_CODE);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("trigger", AdAnalysisTaskTypes.SOURCE);
        input.put("jobId", analysisId);
        input.put("configCode", AdPerformanceAnalysisTriggerPort.CONFIG_CODE);

        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setConfigId(config == null ? null : config.getId());
        row.setWorkflowId(started.workflowId());
        row.setRunId(started.runId());
        row.setWorkflowType(TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS);
        row.setTaskQueue(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS);
        row.setStatus("PENDING");
        row.setPendingAt(now);
        row.setRetryCount(0);
        row.setInputSummary(TaskJsonSupport.toJson(input, "inputSummary"));
        row.setCreatedAt(now);
        executionRepository.insert(row);
    }

    private CreateAdAnalysisResult toCreateResult(GenerationTask task, boolean replay) {
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        String stage = strOr(metadata.get("stage"), task.getStatus());
        if (stage == null || stage.isBlank()) {
            stage = AdAnalysisTaskTypes.STATUS_QUEUED;
        }
        return new CreateAdAnalysisResult(
                task.getId(),
                task.getBusinessId() == null ? "" : task.getBusinessId(),
                externalStatus(task.getStatus()),
                stage,
                iso(task.getCreatedAt()),
                properties.pollUrl(task.getId()),
                replay);
    }

    private AdAnalysisJobView toJobView(GenerationTask task) {
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        String status = externalStatus(task.getStatus());
        String stage = strOr(metadata.get("stage"), status);
        int progress = clampProgress(metadata.get("progress"));
        Map<String, Object> result = null;
        AdAnalysisJobError error = null;
        if (AdAnalysisTaskTypes.STATUS_SUCCEEDED.equals(status)) {
            result = parseObject(task.getResultJson());
        } else if (AdAnalysisTaskTypes.STATUS_FAILED.equals(status)) {
            error = new AdAnalysisJobError(
                    strOr(task.getErrorCode(), "ad_analysis_failed"),
                    strOr(task.getErrorMessage(), "Analysis failed."),
                    false);
        }
        return new AdAnalysisJobView(
                task.getId(),
                task.getBusinessId() == null ? "" : task.getBusinessId(),
                status,
                stage == null ? AdAnalysisTaskTypes.STATUS_QUEUED : stage,
                progress,
                iso(task.getCreatedAt()),
                cleanIso(metadata.get("started_at")),
                cleanIso(metadata.get("completed_at")),
                result,
                error);
    }

    private static Map<String, Object> initialMetadata(String externalRequestId, String payloadHash) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", AdAnalysisTaskTypes.SOURCE);
        metadata.put("external_request_id", externalRequestId);
        metadata.put("payload_hash", payloadHash);
        metadata.put("stage", AdAnalysisTaskTypes.STATUS_QUEUED);
        metadata.put("progress", 0);
        metadata.put("analysis_scope", AdAnalysisTaskTypes.SCOPE);
        metadata.put("dispatch_attempts", 0);
        return metadata;
    }

    private static String newAnalysisId() {
        return "ana_" + UUID.randomUUID().toString().replace("-", "");
    }

    static String externalStatus(String value) {
        if (AdAnalysisTaskTypes.STATUS_QUEUED.equals(value)
                || AdAnalysisTaskTypes.STATUS_PROCESSING.equals(value)
                || AdAnalysisTaskTypes.STATUS_SUCCEEDED.equals(value)
                || AdAnalysisTaskTypes.STATUS_FAILED.equals(value)) {
            return value;
        }
        if ("completed".equals(value) || "success".equals(value)) {
            return AdAnalysisTaskTypes.STATUS_SUCCEEDED;
        }
        if ("running".equals(value)) {
            return AdAnalysisTaskTypes.STATUS_PROCESSING;
        }
        return AdAnalysisTaskTypes.STATUS_QUEUED;
    }

    private static int clampProgress(Object raw) {
        int value = intValue(raw, 0);
        return Math.max(0, Math.min(100, value));
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).strip());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String strOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? fallback : text;
    }

    private static String iso(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static String cleanIso(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private static String writeObject(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "payload serialization failed");
        }
    }

    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, MAP_TYPE);
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
