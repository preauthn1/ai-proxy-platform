package com.yunpei.proxy.service.externalai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.externalai.copy.CopyContextBuilder;
import com.yunpei.proxy.service.externalai.copy.CopyGenerationExecutor;
import com.yunpei.proxy.service.externalai.storyboard.StoryboardContextBuilder;
import com.yunpei.proxy.service.externalai.storyboard.StoryboardGenerationExecutor;
import com.yunpei.proxy.service.externalai.topics.TopicContextBuilder;
import com.yunpei.proxy.service.externalai.topics.TopicSelectionExecutor;
import com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Executor;
import com.yunpei.proxy.service.llm.LlmProvider;
import com.yunpei.proxy.service.llm.LlmProviderFactory;
import com.yunpei.proxy.service.llm.LlmTimeoutException;
import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskJsonSupport;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部 AI 文本生成 Service：创建落库 + 幂等 + 触发 + execute 按 task_type 分发 + GET 只读。
 *
 * <p>对外主路径：工单分析 V3、文案。选题/分镜 executor 仍保留（历史 job GET 与视频内部分镜复用），
 * 对外 HTTP 路由已移除。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class ExternalAiGenerationService {

    public static final String SOURCE_EXTERNAL_AI_GENERATION = "external_ai_generation";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String ERROR_CODE_PROVIDER = "provider_error";
    public static final String ERROR_CODE_UNSUPPORTED = "unsupported_task_type";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_ERROR_LENGTH = 512;
    private static final int MAX_EXTERNAL_REQUEST_ID = 128;

    private final GenerationTaskRepository repository;
    private final AiGenerationTriggerPort triggerPort;
    private final TemporalTaskExecutionRepository executionRepository;
    private final TemporalTaskConfigRepository configRepository;
    private final LlmProviderFactory llmProviderFactory;

    /**
     * 创建 AI 文本任务：校验 task_type → 幂等复用 → 落库 processing → 触发 Workflow。
     */
    public CreateAiJobResult createJob(CreateAiJobCommand command) {
        if (command == null || !ExternalAiTaskTypes.isTextTaskType(command.taskType())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "unsupported task_type");
        }
        String externalRequestId = clean(command.externalRequestId());
        if (externalRequestId != null && externalRequestId.length() > MAX_EXTERNAL_REQUEST_ID) {
            throw BizException.of(ResultCode.PARAM_INVALID, "external_request_id must be at most 128 characters");
        }
        validatePayload(command.taskType(), command.payload());

        GenerationTask existing = repository.findByExternalRequestIdAndType(
                externalRequestId, command.taskType(), ExternalAiTaskTypes.BUSINESS_TYPE);
        if (existing != null) {
            return new CreateAiJobResult(existing.getId(), GenerationTaskRepository.STATUS_PROCESSING);
        }

        String id = UUID.randomUUID().toString();
        Map<String, Object> payload =
                command.payload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(command.payload());
        if (externalRequestId != null) {
            payload.putIfAbsent("external_request_id", externalRequestId);
        }

        GenerationTask row = new GenerationTask();
        row.setId(id);
        row.setTaskType(command.taskType());
        row.setBusinessType(ExternalAiTaskTypes.BUSINESS_TYPE);
        row.setBusinessId(externalRequestId == null ? id : externalRequestId);
        row.setStatus(GenerationTaskRepository.STATUS_PROCESSING);
        row.setPayloadJson(writeObject(payload));
        row.setMetadataJson(writeObject(buildMetadata(command.taskType(), id, externalRequestId)));
        row.setAttemptCount(0);
        repository.insert(row);

        TriggerResult started = triggerPort.start(id);
        if (started != null) {
            insertExecutionSeed(id, started);
        }
        return new CreateAiJobResult(id, GenerationTaskRepository.STATUS_PROCESSING);
    }

    /**
     * 查询任务；未找到或非文本类 task_type → {@link AiGenerationTaskNotFoundException}。
     */
    public AiGenerationJobView getJob(String jobId) {
        GenerationTask task = repository.findById(jobId);
        if (task == null || !ExternalAiTaskTypes.isTextTaskType(task.getTaskType())) {
            throw new AiGenerationTaskNotFoundException("ai generation job not found");
        }
        return toJobView(task);
    }

    /**
     * 执行一次 AI 文本任务并回写（由 Workflow Activity 调用）。
     *
     * <p>幂等：仅 {@code processing} 时执行；按 {@code task_type} 分发钩子（骨架用 mock LLM 组装 result）。
     */
    public Map<String, Object> execute(String jobId) {
        GenerationTask task = repository.findById(jobId);
        if (task == null) {
            throw new AiGenerationTaskNotFoundException("ai generation job not found: " + jobId);
        }
        if (!ExternalAiTaskTypes.isTextTaskType(task.getTaskType())) {
            String message = "unsupported task_type: " + task.getTaskType();
            repository.markFailed(jobId, ERROR_CODE_UNSUPPORTED, truncate(message, MAX_ERROR_LENGTH));
            return linkedOf(
                    "jobId", jobId,
                    "status", STATUS_FAILED,
                    "error_code", ERROR_CODE_UNSUPPORTED,
                    "error_message", message);
        }
        if (!GenerationTaskRepository.STATUS_PROCESSING.equals(task.getStatus())) {
            return existingResultSummary(task);
        }
        try {
            Map<String, Object> result = dispatchExecute(task);
            repository.markSucceeded(jobId, writeObject(result));
            return result;
        } catch (LlmTimeoutException ex) {
            // 保持 processing，让 Temporal 重跑 AiTextGenerationWorkflow activity
            throw ex;
        } catch (RuntimeException ex) {
            return recordProviderFailure(jobId, ex.getMessage());
        }
    }

    /**
     * 终态失败回写（activity 重试耗尽后由 Activity 调用）。
     */
    public Map<String, Object> recordProviderFailure(String jobId, String message) {
        String truncated = truncate(message, MAX_ERROR_LENGTH);
        repository.markFailed(jobId, ERROR_CODE_PROVIDER, truncated);
        return linkedOf(
                "jobId", jobId,
                "status", STATUS_FAILED,
                "error_code", ERROR_CODE_PROVIDER,
                "error_message", truncated);
    }

    /**
     * 按 task_type 分发。
     */
    private Map<String, Object> dispatchExecute(GenerationTask task) {
        String taskType = task.getTaskType();
        Map<String, Object> payload = parseObject(task.getPayloadJson());
        return switch (taskType) {
            case ExternalAiTaskTypes.WORK_ORDER_ANALYSIS_V3 -> executeWorkOrderAnalysisV3(task, payload);
            case ExternalAiTaskTypes.TOPIC_SELECTION -> executeTopics(task, payload);
            case ExternalAiTaskTypes.COPY_GENERATION -> executeCopy(task, payload);
            case ExternalAiTaskTypes.VIDEO_STORYBOARD -> executeStoryboard(task, payload);
            default -> throw BizException.of(ResultCode.PARAM_INVALID, "unsupported task_type: " + taskType);
        };
    }

    private Map<String, Object> executeWorkOrderAnalysisV3(GenerationTask task, Map<String, Object> payload) {
        String requestId = strOr(payload.get("external_request_id"), task.getId());
        return WorkOrderAnalysisV3Executor.execute(
                llm(),
                strOr(payload.get("work_order_text"), ""),
                clean(payload.get("work_order_type")),
                clean(payload.get("media")),
                requestId);
    }

    private Map<String, Object> executeTopics(GenerationTask task, Map<String, Object> payload) {
        String requestId = strOr(payload.get("external_request_id"), task.getId());
        return TopicSelectionExecutor.execute(llm(), payload, requestId);
    }

    private Map<String, Object> executeCopy(GenerationTask task, Map<String, Object> payload) {
        String requestId = strOr(payload.get("external_request_id"), task.getId());
        return CopyGenerationExecutor.execute(llm(), payload, requestId);
    }

    private Map<String, Object> executeStoryboard(GenerationTask task, Map<String, Object> payload) {
        String requestId = strOr(payload.get("external_request_id"), task.getId());
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        String creativeSeed = clean(metadata.get("creative_seed"));
        if (creativeSeed == null) {
            creativeSeed = StoryboardContextBuilder.creativeSeed(requestId);
        }
        return StoryboardGenerationExecutor.execute(llm(), payload, requestId, creativeSeed);
    }

    private LlmProvider llm() {
        return llmProviderFactory.provider();
    }

    private void validatePayload(String taskType, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        switch (taskType) {
            case ExternalAiTaskTypes.WORK_ORDER_ANALYSIS_V3 -> {
                if (clean(body.get("work_order_text")) == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "work_order_text is required");
                }
                if (clean(body.get("work_order_type")) == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "work_order_type is required");
                }
                if (clean(body.get("media")) == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "media is required");
                }
            }
            case ExternalAiTaskTypes.TOPIC_SELECTION -> {
                if (clean(body.get("product_name")) == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "product_name is required");
                }
                int count = TopicContextBuilder.resolveCount(body.get("count"));
                if (count < 1 || count > 5) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "count must be between 1 and 5");
                }
            }
            case ExternalAiTaskTypes.COPY_GENERATION -> {
                if (clean(body.get("product_name")) == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "product_name is required");
                }
                int count = CopyContextBuilder.resolveCount(body.get("count"));
                if (count < CopyContextBuilder.MIN_COUNT || count > CopyContextBuilder.MAX_COUNT) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "count must be between 1 and 5");
                }
            }
            case ExternalAiTaskTypes.VIDEO_STORYBOARD -> validateStoryboardPayload(body);
            default -> throw BizException.of(ResultCode.PARAM_INVALID, "unsupported task_type");
        }
    }

    private void validateStoryboardPayload(Map<String, Object> body) {
        if (clean(body.get("product_name")) == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "product_name is required");
        }
        Object imageUrls = body.get("image_urls");
        if (imageUrls != null) {
            if (!(imageUrls instanceof List<?> list)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image_urls must be a list");
            }
            if (list.size() > StoryboardContextBuilder.MAX_IMAGE_URLS) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image_urls must contain at most 2 items");
            }
            for (Object item : list) {
                if (clean(item) == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "image_urls must contain non-empty strings");
                }
            }
        }
        Object durationRaw = body.get("duration_seconds");
        if (durationRaw != null) {
            Integer duration = parsePositiveInt(durationRaw);
            if (duration == null
                    || duration < StoryboardContextBuilder.MIN_DURATION_SECONDS
                    || duration > StoryboardContextBuilder.MAX_DURATION_SECONDS) {
                throw BizException.of(ResultCode.PARAM_INVALID, "duration_seconds must be between 1 and 300");
            }
        }
        String aspect = clean(body.get("aspect_ratio"));
        if (body.get("aspect_ratio") != null && aspect == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "aspect_ratio must not be blank");
        }
        if (aspect != null && aspect.length() > StoryboardContextBuilder.MAX_ASPECT_RATIO_LENGTH) {
            throw BizException.of(ResultCode.PARAM_INVALID, "aspect_ratio must be at most 32 characters");
        }
    }

    private static Integer parsePositiveInt(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Map<String, Object> buildMetadata(String taskType, String jobId, String externalRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", SOURCE_EXTERNAL_AI_GENERATION);
        metadata.put("external_request_id", externalRequestId);
        metadata.put("task_type", taskType);
        if (ExternalAiTaskTypes.VIDEO_STORYBOARD.equals(taskType)) {
            String identity = externalRequestId != null ? externalRequestId : jobId;
            metadata.putAll(StoryboardContextBuilder.buildMetadata(identity));
        }
        return metadata;
    }

    private void insertExecutionSeed(String jobId, TriggerResult started) {
        LocalDateTime now = TimeZones.now();
        TemporalTaskConfig config = configRepository.findByCode(AiGenerationTriggerPort.CONFIG_CODE);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("trigger", SOURCE_EXTERNAL_AI_GENERATION);
        input.put("jobId", jobId);
        input.put("configCode", AiGenerationTriggerPort.CONFIG_CODE);

        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setConfigId(config == null ? null : config.getId());
        row.setWorkflowId(started.workflowId());
        row.setRunId(started.runId());
        row.setWorkflowType(TemporalWorkflowType.AI_TEXT_GENERATION);
        row.setTaskQueue(TemporalTaskQueue.AI_TEXT_GENERATION);
        row.setStatus("PENDING");
        row.setPendingAt(now);
        row.setStartedAt(null);
        row.setClosedAt(null);
        row.setInputSummary(TaskJsonSupport.toJson(input, "inputSummary"));
        row.setResultSummary(null);
        row.setFailureReason(null);
        row.setRetryCount(0);
        row.setCreatedAt(now);
        executionRepository.insert(row);
    }

    private Map<String, Object> existingResultSummary(GenerationTask task) {
        if (STATUS_SUCCEEDED.equals(task.getStatus())) {
            Map<String, Object> result = parseObject(task.getResultJson());
            if (!result.isEmpty()) {
                result.putIfAbsent("request_id", task.getId());
                return result;
            }
        }
        if (STATUS_FAILED.equals(task.getStatus())) {
            return linkedOf(
                    "jobId", task.getId(),
                    "status", STATUS_FAILED,
                    "error_code", task.getErrorCode(),
                    "error_message", task.getErrorMessage());
        }
        return linkedOf("jobId", task.getId(), "status", task.getStatus());
    }

    private AiGenerationJobView toJobView(GenerationTask task) {
        String status = normalizeStatus(task.getStatus());
        Map<String, Object> result =
                STATUS_SUCCEEDED.equals(status) ? parseObject(task.getResultJson()) : new LinkedHashMap<>();
        String error = STATUS_FAILED.equals(status) ? task.getErrorMessage() : null;
        return new AiGenerationJobView(task.getId(), status, result, error);
    }

    private static String normalizeStatus(String status) {
        if (STATUS_SUCCEEDED.equals(status)) {
            return STATUS_SUCCEEDED;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return GenerationTaskRepository.STATUS_PROCESSING;
    }

    private static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String strOr(Object value, String fallback) {
        String text = clean(value);
        return text == null ? fallback : text;
    }

    private static Map<String, Object> linkedOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
