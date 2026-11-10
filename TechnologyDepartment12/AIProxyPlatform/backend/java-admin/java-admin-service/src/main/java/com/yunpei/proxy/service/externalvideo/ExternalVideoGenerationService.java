package com.yunpei.proxy.service.externalvideo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.externalai.storyboard.StoryboardContextBuilder;
import com.yunpei.proxy.service.externalai.storyboard.StoryboardGenerationExecutor;
import com.yunpei.proxy.service.image.ImageStorage;
import com.yunpei.proxy.service.image.TransferResult;
import com.yunpei.proxy.service.llm.LlmProviderFactory;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.port.VideoGenerationTriggerPort;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskJsonSupport;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import com.yunpei.proxy.service.video.VideoGenerationRequest;
import com.yunpei.proxy.service.video.VideoGenerationStart;
import com.yunpei.proxy.service.video.VideoGenerationStatus;
import com.yunpei.proxy.service.video.VideoProvider;
import com.yunpei.proxy.service.video.VideoProviderException;
import com.yunpei.proxy.service.video.VideoProviderFactory;
import com.yunpei.proxy.service.video.VideoSourceImage;
import com.yunpei.proxy.service.video.VideoStorage;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部视频生成 Service：落库 + Temporal 轮询执行回写。
 *
 * <p>对齐 Java 图片集成的 generation_task 模型；执行模型为 Workflow 定时 poll（GET 只读库）。
 * 无 {@code storyboard_text} 时在 start 阶段内部生成分镜，再送上游视频 Provider。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class ExternalVideoGenerationService {

    public static final String SOURCE_EXTERNAL_VIDEO_GENERATION = "external_video_generation";
    public static final String MODE_TEXT_TO_VIDEO = "text_to_video";
    public static final String MODE_FIRST_LAST_FRAME = "first_last_frame";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String ERROR_CODE_PROVIDER = "provider_error";
    public static final String STORYBOARD_SOURCE_CLIENT = "client";
    public static final String STORYBOARD_SOURCE_GENERATED = "generated";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_ASPECT_RATIO = "9:16";
    private static final int DEFAULT_DURATION = 12;
    private static final int MIN_DURATION = 1;
    private static final int MAX_DURATION = 300;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final int MAX_EXTERNAL_REQUEST_ID = 128;
    private static final int MAX_PROMPT = 4000;

    private final GenerationTaskRepository repository;
    private final VideoProviderFactory providerFactory;
    private final ImageStorage imageStorage;
    private final VideoStorage videoStorage;
    private final VideoGenerationTriggerPort videoGenerationTriggerPort;
    private final TemporalTaskExecutionRepository executionRepository;
    private final TemporalTaskConfigRepository configRepository;
    private final LlmProviderFactory llmProviderFactory;

    /**
     * 创建视频任务：校验 → 物化源图 → 落库 processing → 触发 Workflow。
     *
     * <p>{@code storyboard_text} 可空；为空时要求 {@code product_name}，分镜在 {@link #startProvider} 内生成。
     */
    public CreateVideoJobResult createJob(CreateVideoGenerationCommand command) {
        String storyboard = clean(command.storyboardText());
        String productName = clean(command.productName());
        if (storyboard == null && productName == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "storyboard_text or product_name is required");
        }
        String externalRequestId = clean(command.externalRequestId());
        if (externalRequestId != null && externalRequestId.length() > MAX_EXTERNAL_REQUEST_ID) {
            throw BizException.of(ResultCode.PARAM_INVALID, "external_request_id must be at most 128 characters");
        }
        String prompt = clean(command.prompt());
        if (prompt != null && prompt.length() > MAX_PROMPT) {
            throw BizException.of(ResultCode.PARAM_INVALID, "prompt must be at most 4000 characters");
        }
        int duration = resolveDuration(command.durationSeconds());
        String aspectRatio = clean(command.aspectRatio());
        if (aspectRatio == null) {
            aspectRatio = DEFAULT_ASPECT_RATIO;
        }
        List<String> rawImages = command.images() == null ? List.of() : command.images();
        String generationMode = generationModeForImages(rawImages);

        GenerationTask existing = repository.findByExternalRequestIdAndType(
                externalRequestId,
                GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE,
                GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_VIDEO);
        if (existing != null) {
            return toCreateResult(existing);
        }

        String id = UUID.randomUUID().toString();
        List<String> sourceUrls = materializeSourceImages(rawImages, id);
        String storyboardSource = storyboard == null ? STORYBOARD_SOURCE_GENERATED : STORYBOARD_SOURCE_CLIENT;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_request_id", externalRequestId);
        payload.put("storyboard_text", storyboard);
        payload.put("duration_seconds", duration);
        payload.put("aspect_ratio", aspectRatio);
        payload.put("images", sourceUrls);
        payload.put("generation_mode", generationMode);
        putIfPresent(payload, "product_name", productName);
        putIfPresent(payload, "brief", clean(command.brief()));
        putIfPresent(payload, "prompt", prompt);
        putIfPresent(payload, "language", clean(command.language()));

        GenerationTask row = new GenerationTask();
        row.setId(id);
        row.setTaskType(GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE);
        row.setBusinessType(GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_VIDEO);
        row.setBusinessId(externalRequestId == null ? id : externalRequestId);
        row.setStatus(GenerationTaskRepository.STATUS_PROCESSING);
        row.setPayloadJson(writeObject(payload));
        row.setMetadataJson(writeObject(linkedOf(
                "source", SOURCE_EXTERNAL_VIDEO_GENERATION,
                "external_request_id", externalRequestId,
                "generation_mode", generationMode,
                "duration_seconds", duration,
                "aspect_ratio", aspectRatio,
                "storyboard_source", storyboardSource)));
        row.setAttemptCount(0);
        repository.insert(row);

        TriggerResult started = videoGenerationTriggerPort.start(id);
        if (started != null) {
            insertExecutionSeed(id, started);
        }
        return new CreateVideoJobResult(id, GenerationTaskRepository.STATUS_PROCESSING, duration, aspectRatio);
    }

    /**
     * 查询任务；未找到或不匹配类型 → {@link VideoGenerationTaskNotFoundException}。
     */
    public VideoGenerationJobView getJob(String jobId) {
        GenerationTask task = repository.findById(jobId);
        if (task == null || !GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE.equals(task.getTaskType())) {
            throw new VideoGenerationTaskNotFoundException("video job not found");
        }
        return toJobView(task);
    }

    /**
     * Activity：调用 Provider start，写入 provider_job_id。
     *
     * @return 含 provider_job_id / status 的摘要；若已终态则返回既有结果
     */
    public Map<String, Object> startProvider(String jobId) {
        GenerationTask task = requireVideoTask(jobId);
        if (!GenerationTaskRepository.STATUS_PROCESSING.equals(task.getStatus())) {
            return existingResultSummary(task);
        }
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        String existingProviderJobId = clean(metadata.get("provider_job_id"));
        if (existingProviderJobId != null) {
            return linkedOf(
                    "jobId", jobId,
                    "provider_job_id", existingProviderJobId,
                    "status", GenerationTaskRepository.STATUS_PROCESSING);
        }
        try {
            Map<String, Object> payload = parseObject(task.getPayloadJson());
            ensureStoryboardText(jobId, payload, metadata);
            VideoGenerationRequest request = buildProviderRequest(jobId, payload);
            VideoProvider provider = providerFactory.provider();
            VideoGenerationStart started = provider.startGeneration(request);
            metadata.put("provider_job_id", started.providerJobId());
            metadata.put("provider_status", started.providerStatus());
            metadata.put("implementation_status", "provider_started");
            repository.updateMetadataJson(jobId, writeObject(metadata));
            return linkedOf(
                    "jobId",
                    jobId,
                    "provider_job_id",
                    started.providerJobId(),
                    "status",
                    GenerationTaskRepository.STATUS_PROCESSING,
                    "provider_status",
                    started.providerStatus());
        } catch (RuntimeException ex) {
            String message = truncate(ex.getMessage(), MAX_ERROR_LENGTH);
            repository.markFailed(jobId, ERROR_CODE_PROVIDER, message);
            return linkedOf(
                    "jobId", jobId,
                    "status", STATUS_FAILED,
                    "error_code", ERROR_CODE_PROVIDER,
                    "error_message", message);
        }
    }

    /**
     * 若 payload 无分镜文案，则内部调用分镜生成并写回 payload/metadata。
     */
    private void ensureStoryboardText(String jobId, Map<String, Object> payload, Map<String, Object> metadata) {
        if (clean(payload.get("storyboard_text")) != null) {
            metadata.putIfAbsent("storyboard_source", STORYBOARD_SOURCE_CLIENT);
            return;
        }
        String requestId = strOr(payload.get("external_request_id"), jobId);
        Map<String, Object> storyboardPayload = new LinkedHashMap<>();
        putIfPresent(storyboardPayload, "product_name", clean(payload.get("product_name")));
        putIfPresent(storyboardPayload, "brief", clean(payload.get("brief")));
        putIfPresent(storyboardPayload, "prompt", clean(payload.get("prompt")));
        putIfPresent(storyboardPayload, "language", clean(payload.get("language")));
        storyboardPayload.put("duration_seconds", intOr(payload.get("duration_seconds"), DEFAULT_DURATION));
        storyboardPayload.put("aspect_ratio", strOr(payload.get("aspect_ratio"), DEFAULT_ASPECT_RATIO));
        Object images = payload.get("images");
        if (images instanceof List<?> list) {
            storyboardPayload.put("image_urls", list);
        }
        String creativeSeed = StoryboardContextBuilder.creativeSeed(requestId);
        Map<String, Object> generated = StoryboardGenerationExecutor.execute(
                llmProviderFactory.provider(), storyboardPayload, requestId, creativeSeed);
        String storyboardText = clean(generated.get("storyboard_text"));
        if (storyboardText == null) {
            throw new VideoProviderException("internal storyboard generation returned empty text");
        }
        payload.put("storyboard_text", storyboardText);
        repository.updatePayloadJson(jobId, writeObject(payload));
        metadata.put("storyboard_source", STORYBOARD_SOURCE_GENERATED);
        metadata.put("creative_seed", creativeSeed);
    }

    /**
     * Activity：查询上游一次；succeeded 则 transfer 并 markSucceeded；failed 则 markFailed。
     *
     * @return 当前状态摘要（含 status）
     */
    public Map<String, Object> pollProvider(String jobId) {
        GenerationTask task = requireVideoTask(jobId);
        if (!GenerationTaskRepository.STATUS_PROCESSING.equals(task.getStatus())) {
            return existingResultSummary(task);
        }
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        String providerJobId = clean(metadata.get("provider_job_id"));
        if (providerJobId == null) {
            String message = "provider_job_id is missing";
            repository.markFailed(jobId, ERROR_CODE_PROVIDER, message);
            return linkedOf(
                    "jobId", jobId,
                    "status", STATUS_FAILED,
                    "error_code", ERROR_CODE_PROVIDER,
                    "error_message", message);
        }
        try {
            VideoProvider provider = providerFactory.provider();
            VideoGenerationStatus status = provider.getGenerationStatus(providerJobId);
            metadata.put("provider_status", status.providerStatus());
            if ("succeeded".equals(status.providerStatus())) {
                String videoUrl = clean(status.videoUrl());
                if (videoUrl == null) {
                    throw new VideoProviderException("provider succeeded without video url");
                }
                TransferResult transfer = videoStorage.transfer(videoUrl, jobId);
                Map<String, Object> payload = parseObject(task.getPayloadJson());
                int duration = intOr(payload.get("duration_seconds"), DEFAULT_DURATION);
                String aspectRatio = strOr(payload.get("aspect_ratio"), DEFAULT_ASPECT_RATIO);
                Map<String, Object> result = linkedOf(
                        "url", transfer.publicUrl() == null ? videoUrl : transfer.publicUrl(),
                        "storage_key", transfer.storageKey(),
                        "duration_seconds", duration,
                        "aspect_ratio", aspectRatio,
                        "provider_job_id", providerJobId,
                        "jobId", jobId);
                repository.markSucceeded(jobId, writeObject(result));
                metadata.put("implementation_status", "succeeded");
                repository.updateMetadataJson(jobId, writeObject(metadata));
                return result;
            }
            if ("failed".equals(status.providerStatus())) {
                String message = truncate(
                        status.errorMessage() == null ? "video generation failed" : status.errorMessage(),
                        MAX_ERROR_LENGTH);
                repository.markFailed(jobId, ERROR_CODE_PROVIDER, message);
                metadata.put("implementation_status", "provider_failed");
                repository.updateMetadataJson(jobId, writeObject(metadata));
                return linkedOf(
                        "jobId", jobId,
                        "status", STATUS_FAILED,
                        "error_code", ERROR_CODE_PROVIDER,
                        "error_message", message);
            }
            repository.updateMetadataJson(jobId, writeObject(metadata));
            return linkedOf(
                    "jobId",
                    jobId,
                    "provider_job_id",
                    providerJobId,
                    "status",
                    GenerationTaskRepository.STATUS_PROCESSING,
                    "provider_status",
                    status.providerStatus());
        } catch (RuntimeException ex) {
            String message = truncate(ex.getMessage(), MAX_ERROR_LENGTH);
            repository.markFailed(jobId, ERROR_CODE_PROVIDER, message);
            return linkedOf(
                    "jobId", jobId,
                    "status", STATUS_FAILED,
                    "error_code", ERROR_CODE_PROVIDER,
                    "error_message", message);
        }
    }

    private VideoGenerationRequest buildProviderRequest(String jobId, Map<String, Object> payload) {
        String prompt = strOr(payload.get("storyboard_text"), "");
        int duration = intOr(payload.get("duration_seconds"), DEFAULT_DURATION);
        String aspectRatio = strOr(payload.get("aspect_ratio"), DEFAULT_ASPECT_RATIO);
        List<VideoSourceImage> sources = new ArrayList<>();
        Object rawImages = payload.get("images");
        if (rawImages instanceof List<?> list) {
            String[] roles = {"first_frame", "last_frame"};
            int index = 0;
            for (Object item : list) {
                String url = clean(item);
                if (url == null) {
                    continue;
                }
                String role = index < roles.length ? roles[index] : "reference";
                sources.add(new VideoSourceImage(jobId + "-" + (index + 1), url, role));
                index++;
            }
        }
        return new VideoGenerationRequest(
                prompt,
                sources,
                duration,
                aspectRatio,
                Map.of("job_id", jobId, "source", SOURCE_EXTERNAL_VIDEO_GENERATION));
    }

    private List<String> materializeSourceImages(List<String> images, String jobId) {
        List<String> out = new ArrayList<>();
        int index = 1;
        for (String raw : images) {
            String value = clean(raw);
            if (value == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image value is required");
            }
            if (looksLikeHttpUrl(value)) {
                validatePublicHttpsUrl(value);
                out.add(value);
            } else {
                String dataUrl = toDataUrl(value);
                TransferResult transfer = imageStorage.transfer(dataUrl, jobId + "-src", index);
                String publicUrl = transfer.publicUrl();
                if (publicUrl == null || publicUrl.isBlank()) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "failed to store source image");
                }
                out.add(publicUrl);
            }
            index++;
        }
        return out;
    }

    private static String generationModeForImages(List<String> images) {
        int size = images == null ? 0 : images.size();
        if (size == 0) {
            return MODE_TEXT_TO_VIDEO;
        }
        if (size == 2) {
            return MODE_FIRST_LAST_FRAME;
        }
        throw BizException.of(
                ResultCode.PARAM_INVALID,
                "images must be omitted, empty, or contain exactly 2 base64 or public HTTPS images");
    }

    private static void validatePublicHttpsUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || (uri.getFragment() != null && !uri.getFragment().isEmpty())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image URL must be a valid public HTTPS URL");
            }
        } catch (IllegalArgumentException ex) {
            throw BizException.of(ResultCode.PARAM_INVALID, "image URL must be a valid public HTTPS URL");
        }
    }

    private static boolean looksLikeHttpUrl(String value) {
        return value.contains("://");
    }

    private static String toDataUrl(String value) {
        String text = value.trim();
        String mimeType = "image/jpeg";
        String encoded = text;
        if (text.startsWith("data:")) {
            int comma = text.indexOf(',');
            if (comma < 0 || !text.regionMatches(true, 0, "data:", 0, 5) || !text.contains(";base64")) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image data URL must use base64 encoding");
            }
            String header = text.substring(5, comma);
            int semi = header.indexOf(';');
            mimeType = semi > 0 ? header.substring(0, semi).trim() : header.trim();
            if (mimeType.isEmpty() || !mimeType.startsWith("image/")) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image data URL must use an image MIME type");
            }
            encoded = text.substring(comma + 1);
        }
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length == 0) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image base64 value is required");
            }
            String sniffed = sniffMime(data);
            if (sniffed != null) {
                mimeType = sniffed;
            }
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (IllegalArgumentException ex) {
            throw BizException.of(ResultCode.PARAM_INVALID, "image must be valid base64 or a public HTTPS URL");
        }
    }

    private static String sniffMime(byte[] data) {
        if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private void insertExecutionSeed(String jobId, TriggerResult started) {
        LocalDateTime now = TimeZones.now();
        TemporalTaskConfig config = configRepository.findByCode(VideoGenerationTriggerPort.CONFIG_CODE);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("trigger", SOURCE_EXTERNAL_VIDEO_GENERATION);
        input.put("jobId", jobId);
        input.put("configCode", VideoGenerationTriggerPort.CONFIG_CODE);

        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setConfigId(config == null ? null : config.getId());
        row.setWorkflowId(started.workflowId());
        row.setRunId(started.runId());
        row.setWorkflowType(TemporalWorkflowType.VIDEO_GENERATION);
        row.setTaskQueue(TemporalTaskQueue.VIDEO_GENERATION);
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

    private GenerationTask requireVideoTask(String jobId) {
        GenerationTask task = repository.findById(jobId);
        if (task == null || !GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE.equals(task.getTaskType())) {
            throw new VideoGenerationTaskNotFoundException("video generation job not found: " + jobId);
        }
        return task;
    }

    private CreateVideoJobResult toCreateResult(GenerationTask existing) {
        Map<String, Object> payload = parseObject(existing.getPayloadJson());
        int duration = intOr(payload.get("duration_seconds"), DEFAULT_DURATION);
        String aspectRatio = strOr(payload.get("aspect_ratio"), DEFAULT_ASPECT_RATIO);
        return new CreateVideoJobResult(existing.getId(), existing.getStatus(), duration, aspectRatio);
    }

    private VideoGenerationJobView toJobView(GenerationTask task) {
        Map<String, Object> payload = parseObject(task.getPayloadJson());
        Map<String, Object> result = parseObject(task.getResultJson());
        int duration = intOr(result.get("duration_seconds"), intOr(payload.get("duration_seconds"), DEFAULT_DURATION));
        String aspectRatio =
                strOr(result.get("aspect_ratio"), strOr(payload.get("aspect_ratio"), DEFAULT_ASPECT_RATIO));
        String status = normalizeStatus(task.getStatus());
        String url = STATUS_SUCCEEDED.equals(status) ? clean(result.get("url")) : null;
        String error = STATUS_FAILED.equals(status) ? task.getErrorMessage() : null;
        return new VideoGenerationJobView(task.getId(), status, duration, aspectRatio, url, error);
    }

    private Map<String, Object> existingResultSummary(GenerationTask task) {
        if (STATUS_SUCCEEDED.equals(task.getStatus())) {
            Map<String, Object> result = parseObject(task.getResultJson());
            if (!result.isEmpty()) {
                result.putIfAbsent("jobId", task.getId());
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

    private static int resolveDuration(Integer duration) {
        int value = duration == null ? DEFAULT_DURATION : duration;
        if (value < MIN_DURATION || value > MAX_DURATION) {
            throw BizException.of(ResultCode.PARAM_INVALID, "duration_seconds must be between 1 and 300");
        }
        return value;
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

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isEmpty())) {
            map.put(key, value);
        }
    }

    private static String strOr(Object value, Object fallback) {
        String text = clean(value);
        return text == null ? clean(fallback) : text;
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static Map<String, Object> linkedOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
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
