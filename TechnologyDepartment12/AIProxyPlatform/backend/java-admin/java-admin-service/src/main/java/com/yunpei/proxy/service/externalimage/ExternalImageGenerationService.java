package com.yunpei.proxy.service.externalimage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.image.GeneratedImage;
import com.yunpei.proxy.service.image.ImageBrief;
import com.yunpei.proxy.service.image.ImageContentType;
import com.yunpei.proxy.service.image.ImageContentTypes;
import com.yunpei.proxy.service.image.ImageProvider;
import com.yunpei.proxy.service.image.ImageProviderException;
import com.yunpei.proxy.service.image.ImageProviderFactory;
import com.yunpei.proxy.service.image.ImageProviderProperties;
import com.yunpei.proxy.service.image.ImageStorage;
import com.yunpei.proxy.service.image.TransferResult;
import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskJsonSupport;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 图片生成任务 Service：generate / edit(from_image) / revision。
 *
 * <p>对齐 Python {@code ExternalImageGenerationService}：落库 processing、external_request_id 幂等、
 * Temporal 触发；payload <strong>不</strong>长期存放 reference data URL，execute 时按
 * {@code source_storage_key} 解析。生成结果写入 {@code storage_key} 供 revision 使用。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class ExternalImageGenerationService {

    public static final String SOURCE_EXTERNAL_IMAGE_GENERATION = "external_image_generation";
    public static final String SOURCE_EXTERNAL_IMAGE_EDIT = "external_image_edit";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String ERROR_CODE_PROVIDER = "provider_error";
    public static final String ERROR_CODE_CONTENT_POLICY = "content_policy_violation";
    public static final String MODE_GENERATE = "generate";
    public static final String MODE_EDIT = "edit";
    public static final String MODE_FROM_IMAGE = "from_image";

    private static final String EXTERNAL_IMAGE_CONTENT_POLICY_ERROR = "非常抱歉，该提示可能违反了我们的内容政策。" + "如果你认为此判断有误，请重试或修改提示语。";
    private static final String EXTERNAL_IMAGE_GENERIC_ERROR = "图片生成失败，请稍后重试。";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_SIZE = "9:16";
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 5;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final int MAX_FEEDBACK_LENGTH = 2000;
    private static final int MAX_ID_LENGTH = 128;

    private final GenerationTaskRepository repository;
    private final ImageProviderFactory providerFactory;
    private final ImageStorage imageStorage;
    private final ImageGenerationTriggerPort imageGenerationTriggerPort;
    private final TemporalTaskExecutionRepository executionRepository;
    private final TemporalTaskConfigRepository configRepository;
    private final ImageProviderProperties imageProviderProperties;

    /**
     * 创建文生图任务。
     *
     * @param command 入参
     * @return 建任务结果
     */
    public CreateJobResult createJob(CreateImageGenerationCommand command) {
        String prompt = clean(command.prompt());
        if (prompt == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "prompt is required");
        }
        String externalRequestId = cleanBounded(command.externalRequestId(), MAX_ID_LENGTH);
        int count = resolveCount(command.count());
        String size = clean(command.size());
        if (size == null) {
            size = DEFAULT_SIZE;
        }
        String modelId = cleanBounded(command.modelId(), MAX_ID_LENGTH);

        CreateJobResult reused = tryReuse(externalRequestId);
        if (reused != null) {
            return reused;
        }

        Map<String, Object> payload = linkedOf(
                "external_request_id", externalRequestId,
                "prompt", prompt,
                "count", count,
                "size", size,
                "model_id", modelId);
        Map<String, Object> metadata = linkedOf(
                "source", SOURCE_EXTERNAL_IMAGE_GENERATION,
                "external_request_id", externalRequestId,
                "count", count,
                "size", size,
                "model_id", modelId,
                "mode", MODE_GENERATE);
        return persistAndTrigger(externalRequestId, payload, metadata, count, size, MODE_GENERATE, null);
    }

    /**
     * 创建上传图编辑任务（from_image）。
     *
     * @param command 含图片字节与 form 字段
     * @return 建任务结果
     */
    public CreateJobResult createEditJob(CreateImageEditCommand command) {
        if (command == null || command.imageBytes() == null || command.imageBytes().length == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Uploaded image is empty");
        }
        long maxBytes = imageProviderProperties.getUploadMaxBytes() <= 0
                ? 10L * 1024 * 1024
                : imageProviderProperties.getUploadMaxBytes();
        if (command.imageBytes().length > maxBytes) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Uploaded image exceeds configured size limit");
        }
        String declared = ImageContentTypes.normalizeDeclaredContentType(command.declaredContentType());
        if (declared == null || !ImageContentTypes.isAllowedUploadContentType(command.declaredContentType())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Unsupported image content type");
        }
        ImageContentType detected;
        try {
            detected = ImageContentTypes.detect(command.imageBytes());
        } catch (ImageProviderException ex) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Uploaded image content is invalid or unsupported");
        }

        String prompt = clean(command.prompt());
        if (prompt == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "prompt is required");
        }
        String externalRequestId = cleanBounded(command.externalRequestId(), MAX_ID_LENGTH);
        CreateJobResult reused = tryReuse(externalRequestId);
        if (reused != null) {
            return reused;
        }

        TransferResult stored;
        try {
            stored = imageStorage.storeUploadedSourceImage(command.imageBytes(), detected.mimeType());
        } catch (ImageProviderException ex) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    ex.getMessage() == null ? "source image is unavailable" : ex.getMessage());
        }
        if (stored == null || clean(stored.storageKey()) == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image is unavailable");
        }
        // 创建时校验可读；不把 data URL 写入 payload
        if (imageStorage.dataUrlForStorageKey(stored.storageKey()) == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image is unavailable");
        }

        int count = resolveCount(command.count());
        String size = clean(command.size());
        if (size == null) {
            size = DEFAULT_SIZE;
        }
        String modelId = cleanBounded(command.modelId(), MAX_ID_LENGTH);
        String sourceStorageKey = stored.storageKey();

        Map<String, Object> payload = linkedOf(
                "mode", MODE_FROM_IMAGE,
                "external_request_id", externalRequestId,
                "source_storage_key", sourceStorageKey,
                "prompt", prompt,
                "count", count,
                "size", size,
                "model_id", modelId);
        Map<String, Object> metadata = linkedOf(
                "source", SOURCE_EXTERNAL_IMAGE_EDIT,
                "external_request_id", externalRequestId,
                "mode", MODE_FROM_IMAGE,
                "source_storage_key", sourceStorageKey,
                "count", count,
                "size", size,
                "model_id", modelId);
        return persistAndTrigger(externalRequestId, payload, metadata, count, size, MODE_FROM_IMAGE, null);
    }

    /**
     * 基于已成功源 job 创建修订任务。
     *
     * @param command 修订入参
     * @return 建任务结果
     */
    public CreateJobResult createRevisionJob(CreateImageRevisionCommand command) {
        String feedback = clean(command.feedback());
        if (feedback == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "feedback is required");
        }
        if (feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw BizException.of(ResultCode.PARAM_INVALID, "feedback must be at most 2000 characters");
        }
        String externalRequestId = cleanBounded(command.externalRequestId(), MAX_ID_LENGTH);
        CreateJobResult reused = tryReuse(externalRequestId);
        if (reused != null) {
            return reused;
        }

        String sourceJobId = clean(command.sourceJobId());
        if (sourceJobId == null) {
            throw new GenerationTaskNotFoundException("source image generation job not found");
        }
        GenerationTask sourceTask = repository.findById(sourceJobId);
        if (sourceTask == null
                || !GenerationTaskRepository.TASK_TYPE_EXTERNAL_IMAGE_GENERATE.equals(sourceTask.getTaskType())) {
            throw new GenerationTaskNotFoundException("source image generation job not found");
        }
        if (!STATUS_SUCCEEDED.equals(sourceTask.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image generation job is not succeeded");
        }

        Map<String, Object> sourcePayload = parseObject(sourceTask.getPayloadJson());
        Map<String, Object> sourceResult = parseObject(sourceTask.getResultJson());
        int sourceImageIndex = command.sourceImageIndex() == null ? 1 : command.sourceImageIndex();
        if (sourceImageIndex < 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source_image_index must be >= 1");
        }
        Map<String, Object> sourceImage = sourceImageForIndex(sourceResult, sourceImageIndex);
        String sourceStorageKey = clean(sourceImage.get("storage_key"));
        if (sourceStorageKey == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image storage_key is required");
        }
        if (imageStorage.dataUrlForStorageKey(sourceStorageKey) == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image is unavailable");
        }

        String sourcePrompt =
                firstNonBlank(sourceImage.get("prompt"), sourceResult.get("prompt"), sourcePayload.get("prompt"));
        if (sourcePrompt == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image prompt is required");
        }

        int count = resolveRevisionCount(command.count(), sourceResult, sourcePayload);
        String size = firstNonBlank(
                command.size(), sourceImage.get("size"), sourceResult.get("size"), sourcePayload.get("size"));
        if (size == null) {
            size = DEFAULT_SIZE;
        }
        String modelId = firstNonBlank(
                cleanBounded(command.modelId(), MAX_ID_LENGTH),
                sourceResult.get("model_id"),
                sourcePayload.get("model_id"),
                sourceImage.get("model"));
        String revisedPrompt = sourcePrompt + "\n\n修改要求：" + feedback;
        String mode = initialRevisionMode(modelId);

        Map<String, Object> payload = linkedOf(
                "is_revision", true,
                "external_request_id", externalRequestId,
                "source_job_id", sourceTask.getId(),
                "source_image_index", sourceImageIndex,
                "source_storage_key", sourceStorageKey,
                "revision_feedback", feedback,
                "prompt", sourcePrompt,
                "revised_prompt", revisedPrompt,
                "count", count,
                "size", size,
                "model_id", modelId);
        Map<String, Object> metadata = linkedOf(
                "source", SOURCE_EXTERNAL_IMAGE_GENERATION,
                "external_request_id", externalRequestId,
                "is_revision", true,
                "source_job_id", sourceTask.getId(),
                "source_image_index", sourceImageIndex,
                "count", count,
                "size", size,
                "model_id", modelId,
                "mode", mode);
        return persistAndTrigger(externalRequestId, payload, metadata, count, size, mode, sourceTask.getId());
    }

    /**
     * 查询任务详情；未找到或 task_type 不匹配 → {@link GenerationTaskNotFoundException}。
     *
     * @param jobId 任务 id
     * @return 任务详情视图
     */
    public GenerationJobView getJob(String jobId) {
        GenerationTask task = repository.findById(jobId);
        if (task == null || !GenerationTaskRepository.TASK_TYPE_EXTERNAL_IMAGE_GENERATE.equals(task.getTaskType())) {
            throw new GenerationTaskNotFoundException("image generation job not found");
        }
        return toJobView(task);
    }

    /**
     * 执行一次图片生成并回写状态（由 ImageGeneration activity 调用）。
     *
     * @param jobId 任务 id
     * @return 成功时为 result 对象；失败时含 error 字段
     */
    public Map<String, Object> execute(String jobId) {
        GenerationTask task = repository.findById(jobId);
        if (task == null) {
            throw new GenerationTaskNotFoundException("image generation job not found: " + jobId);
        }
        if (!GenerationTaskRepository.STATUS_PROCESSING.equals(task.getStatus())) {
            return existingResultSummary(task);
        }
        try {
            Map<String, Object> payload = parseObject(task.getPayloadJson());
            boolean isRevision = boolOf(payload.get("is_revision"));
            String payloadMode = cleanMode(payload.get("mode"));
            boolean isFromImage = MODE_FROM_IMAGE.equals(payloadMode);

            String prompt;
            int count;
            String size;
            String modelId;
            String externalRequestId;
            String sourceJobId = null;
            Integer sourceImageIndex = null;
            String sourceStorageKey = null;
            String referenceImageDataUrl = null;
            String revisionInstruction = null;
            String resultMode;

            if (isRevision) {
                prompt = requiredText(payload.get("revised_prompt"), "prompt");
                count = resolveCount(intOr(payload.get("count"), 1));
                size = strOr(payload.get("size"), DEFAULT_SIZE);
                modelId = clean(strOr(payload.get("model_id"), null));
                externalRequestId = clean(payload.get("external_request_id"));
                sourceJobId = clean(payload.get("source_job_id"));
                sourceImageIndex = intOr(payload.get("source_image_index"), 1);
                sourceStorageKey = clean(payload.get("source_storage_key"));
                referenceImageDataUrl = resolveReferenceDataUrl(sourceStorageKey);
                revisionInstruction = requiredText(payload.get("revision_feedback"), "feedback");
                resultMode = cleanMode(parseObject(task.getMetadataJson()).get("mode"));
                if (resultMode == null) {
                    resultMode = initialRevisionMode(modelId);
                }
            } else if (isFromImage) {
                prompt = requiredText(payload.get("prompt"), "prompt");
                count = resolveCount(intOr(payload.get("count"), 1));
                size = strOr(payload.get("size"), DEFAULT_SIZE);
                modelId = clean(strOr(payload.get("model_id"), null));
                externalRequestId = clean(payload.get("external_request_id"));
                sourceStorageKey = requiredText(payload.get("source_storage_key"), "source_storage_key");
                referenceImageDataUrl = resolveReferenceDataUrl(sourceStorageKey);
                revisionInstruction = prompt;
                resultMode = MODE_FROM_IMAGE;
            } else {
                prompt = requiredText(payload.get("prompt"), "prompt");
                count = resolveCount(intOr(payload.get("count"), 1));
                size = strOr(payload.get("size"), DEFAULT_SIZE);
                modelId = clean(strOr(payload.get("model_id"), null));
                externalRequestId = clean(payload.get("external_request_id"));
                resultMode = MODE_GENERATE;
            }

            ImageProvider provider = providerFactory.provider();
            List<Map<String, Object>> images = generateAllParallel(
                    provider,
                    jobId,
                    count,
                    size,
                    prompt,
                    modelId,
                    referenceImageDataUrl,
                    revisionInstruction,
                    isRevision,
                    isFromImage,
                    sourceJobId,
                    sourceImageIndex,
                    sourceStorageKey,
                    externalRequestId,
                    resultMode);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("images", images);
            result.put("count", count);
            result.put("size", size);
            result.put("model_id", modelId);
            result.put("jobId", jobId);
            if (isRevision) {
                result.put("source_job_id", sourceJobId);
                result.put("mode", resultMode);
            } else if (isFromImage) {
                result.put("mode", MODE_FROM_IMAGE);
                result.put("source_storage_key", sourceStorageKey);
            }

            repository.markSucceeded(jobId, writeObject(result));
            return result;
        } catch (RuntimeException ex) {
            String message = truncate(ex.getMessage(), MAX_ERROR_LENGTH);
            String errorCode = classifyErrorCode(message);
            repository.markFailed(jobId, errorCode, message);
            return linkedOf(
                    "jobId", jobId,
                    "status", STATUS_FAILED,
                    "error_code", errorCode,
                    "error_message", message);
        }
    }

    private String resolveReferenceDataUrl(String sourceStorageKey) {
        String key = clean(sourceStorageKey);
        if (key == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image is unavailable");
        }
        String dataUrl = imageStorage.dataUrlForStorageKey(key);
        if (dataUrl == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image is unavailable");
        }
        return dataUrl;
    }

    private CreateJobResult tryReuse(String externalRequestId) {
        if (externalRequestId == null) {
            return null;
        }
        GenerationTask existing = repository.findByExternalRequestIdAndType(
                externalRequestId,
                GenerationTaskRepository.TASK_TYPE_EXTERNAL_IMAGE_GENERATE,
                GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_IMAGE);
        if (existing == null) {
            return null;
        }
        return toCreateResult(existing);
    }

    private CreateJobResult persistAndTrigger(
            String externalRequestId,
            Map<String, Object> payload,
            Map<String, Object> metadata,
            int count,
            String size,
            String mode,
            String sourceJobId) {
        String id = UUID.randomUUID().toString();
        GenerationTask row = new GenerationTask();
        row.setId(id);
        row.setTaskType(GenerationTaskRepository.TASK_TYPE_EXTERNAL_IMAGE_GENERATE);
        row.setBusinessType(GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_IMAGE);
        row.setBusinessId(externalRequestId == null ? id : externalRequestId);
        row.setStatus(GenerationTaskRepository.STATUS_PROCESSING);
        row.setPayloadJson(writeObject(payload));
        row.setMetadataJson(writeObject(metadata));
        row.setAttemptCount(0);
        repository.insert(row);
        TriggerResult started = imageGenerationTriggerPort.start(id);
        if (started != null) {
            insertExecutionSeed(id, started);
        }
        return new CreateJobResult(id, GenerationTaskRepository.STATUS_PROCESSING, count, size, mode, sourceJobId);
    }

    private void insertExecutionSeed(String jobId, TriggerResult started) {
        LocalDateTime now = TimeZones.now();
        TemporalTaskConfig config = configRepository.findByCode(ImageGenerationTriggerPort.CONFIG_CODE);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("trigger", SOURCE_EXTERNAL_IMAGE_GENERATION);
        input.put("jobId", jobId);
        input.put("configCode", ImageGenerationTriggerPort.CONFIG_CODE);

        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setConfigId(config == null ? null : config.getId());
        row.setWorkflowId(started.workflowId());
        row.setRunId(started.runId());
        row.setWorkflowType(TemporalWorkflowType.IMAGE_GENERATION);
        row.setTaskQueue(TemporalTaskQueue.IMAGE_GENERATION);
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

    private List<Map<String, Object>> generateAllParallel(
            ImageProvider provider,
            String jobId,
            int count,
            String size,
            String prompt,
            String modelId,
            String referenceImageDataUrl,
            String revisionInstruction,
            boolean isRevision,
            boolean isFromImage,
            String sourceJobId,
            Integer sourceImageIndex,
            String sourceStorageKey,
            String externalRequestId,
            String resultMode) {
        if (count <= 1) {
            return List.of(generateOne(
                    provider,
                    jobId,
                    1,
                    size,
                    prompt,
                    modelId,
                    referenceImageDataUrl,
                    revisionInstruction,
                    isRevision,
                    isFromImage,
                    sourceJobId,
                    sourceImageIndex,
                    sourceStorageKey,
                    externalRequestId,
                    resultMode));
        }
        List<Future<GeneratedImage>> futures = new ArrayList<>(count);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 1; index <= count; index++) {
                final int idx = index;
                futures.add(executor.submit(() -> {
                    ImageBrief brief =
                            new ImageBrief(idx, size, prompt, modelId, referenceImageDataUrl, revisionInstruction);
                    return provider.generate(brief);
                }));
            }
            List<GeneratedImage> generatedList = new ArrayList<>(count);
            RuntimeException firstError = null;
            for (Future<GeneratedImage> future : futures) {
                try {
                    generatedList.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    firstError = new ImageProviderException("image generation interrupted", e);
                    break;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (cause instanceof RuntimeException re) {
                        firstError = re;
                    } else {
                        firstError = new ImageProviderException(
                                cause.getMessage() == null ? "image generation failed" : cause.getMessage(), cause);
                    }
                    break;
                }
            }
            if (firstError != null) {
                for (Future<GeneratedImage> future : futures) {
                    future.cancel(true);
                }
                throw firstError;
            }
            generatedList.sort(Comparator.comparingInt(GeneratedImage::index));
            List<Map<String, Object>> images = new ArrayList<>(generatedList.size());
            for (GeneratedImage generated : generatedList) {
                images.add(toImageResult(
                        generated,
                        jobId,
                        prompt,
                        size,
                        isRevision,
                        isFromImage,
                        sourceJobId,
                        sourceImageIndex,
                        sourceStorageKey,
                        externalRequestId,
                        resultMode));
            }
            return images;
        }
    }

    private Map<String, Object> generateOne(
            ImageProvider provider,
            String jobId,
            int index,
            String size,
            String prompt,
            String modelId,
            String referenceImageDataUrl,
            String revisionInstruction,
            boolean isRevision,
            boolean isFromImage,
            String sourceJobId,
            Integer sourceImageIndex,
            String sourceStorageKey,
            String externalRequestId,
            String resultMode) {
        ImageBrief brief = new ImageBrief(index, size, prompt, modelId, referenceImageDataUrl, revisionInstruction);
        GeneratedImage generated = provider.generate(brief);
        return toImageResult(
                generated,
                jobId,
                prompt,
                size,
                isRevision,
                isFromImage,
                sourceJobId,
                sourceImageIndex,
                sourceStorageKey,
                externalRequestId,
                resultMode);
    }

    private Map<String, Object> toImageResult(
            GeneratedImage generated,
            String jobId,
            String prompt,
            String size,
            boolean isRevision,
            boolean isFromImage,
            String sourceJobId,
            Integer sourceImageIndex,
            String sourceStorageKey,
            String externalRequestId,
            String resultMode) {
        int index = generated.index();
        TransferResult transfer = imageStorage.transfer(generated.url(), jobId, index);
        String publicUrl = transfer.publicUrl() == null ? generated.url() : transfer.publicUrl();
        String storageKey = transfer.storageKey() != null ? transfer.storageKey() : generated.storageKey();

        Map<String, Object> imageMeta = new LinkedHashMap<>();
        if (generated.metadata() != null) {
            imageMeta.putAll(generated.metadata());
        }
        if (isRevision) {
            String mode = cleanMode(imageMeta.get("mode"));
            if (mode == null) {
                mode = resultMode;
            }
            imageMeta.put("source_job_id", sourceJobId);
            imageMeta.put("source_image_index", sourceImageIndex);
            imageMeta.put("mode", mode);
        } else if (isFromImage) {
            String providerMode = cleanMode(imageMeta.get("mode"));
            imageMeta.put("mode", MODE_FROM_IMAGE);
            imageMeta.put("provider_mode", providerMode);
            imageMeta.put("source_storage_key", sourceStorageKey);
        }
        imageMeta.put("source", isFromImage ? SOURCE_EXTERNAL_IMAGE_EDIT : SOURCE_EXTERNAL_IMAGE_GENERATION);
        imageMeta.put("external_request_id", externalRequestId);

        Map<String, Object> image = linkedOf(
                "index", index,
                "url", publicUrl,
                "storage_key", storageKey,
                "prompt", generated.prompt() == null ? prompt : generated.prompt(),
                "size", generated.size() == null ? size : generated.size());
        if (generated.model() != null) {
            image.put("model", generated.model());
        }
        image.put("metadata", imageMeta);
        return image;
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

    private CreateJobResult toCreateResult(GenerationTask existing) {
        Map<String, Object> payload = parseObject(existing.getPayloadJson());
        Map<String, Object> metadata = parseObject(existing.getMetadataJson());
        int count = payload.get("count") instanceof Number n ? n.intValue() : 1;
        Object rawSize = payload.get("size");
        String size = rawSize == null ? DEFAULT_SIZE : String.valueOf(rawSize);
        String mode = cleanMode(firstNonBlank(payload.get("mode"), metadata.get("mode")));
        String sourceJobId = clean(firstNonBlank(payload.get("source_job_id"), metadata.get("source_job_id")));
        return new CreateJobResult(existing.getId(), existing.getStatus(), count, size, mode, sourceJobId);
    }

    private GenerationJobView toJobView(GenerationTask task) {
        Map<String, Object> payload = parseObject(task.getPayloadJson());
        Map<String, Object> result = parseObject(task.getResultJson());
        Map<String, Object> metadata = parseObject(task.getMetadataJson());
        int count = intOr(result.get("count"), intOr(payload.get("count"), 1));
        String size = strOr(result.get("size"), strOr(payload.get("size"), DEFAULT_SIZE));
        String modelId = clean(strOr(result.get("model_id"), payload.get("model_id")));
        String status = normalizeStatus(task.getStatus());
        List<GeneratedImageItem> images = status.equals(STATUS_SUCCEEDED) ? parseImages(result) : List.of();
        String error = status.equals(STATUS_FAILED) ? publicError(task.getErrorCode()) : null;
        String sourceJobId = clean(firstNonBlank(result.get("source_job_id"), payload.get("source_job_id")));
        String mode = cleanMode(firstNonBlank(result.get("mode"), payload.get("mode"), metadata.get("mode")));
        return new GenerationJobView(task.getId(), status, count, size, modelId, images, error, sourceJobId, mode);
    }

    static String publicError(String errorCode) {
        if (ERROR_CODE_CONTENT_POLICY.equals(errorCode)) {
            return EXTERNAL_IMAGE_CONTENT_POLICY_ERROR;
        }
        return EXTERNAL_IMAGE_GENERIC_ERROR;
    }

    private static String classifyErrorCode(String message) {
        if (message != null
                && message.toLowerCase().contains("content")
                && message.toLowerCase().contains("policy")) {
            return ERROR_CODE_CONTENT_POLICY;
        }
        return ERROR_CODE_PROVIDER;
    }

    private String initialRevisionMode(String modelId) {
        // new2api + editEnabled → edit；否则 generate（参考图仍传入 brief）
        if (imageProviderProperties != null
                && imageProviderProperties.isNew2api()
                && imageProviderProperties.getNew2api() != null
                && imageProviderProperties.getNew2api().isEditEnabled()) {
            return MODE_EDIT;
        }
        return MODE_GENERATE;
    }

    private static Map<String, Object> sourceImageForIndex(Map<String, Object> result, int sourceImageIndex) {
        Object rawImages = result.get("images");
        if (!(rawImages instanceof List<?> list) || list.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "source image generation job has no images");
        }
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> m)) {
                continue;
            }
            Object index = m.get("index");
            if (index instanceof Number n && n.intValue() == sourceImageIndex) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
        }
        throw BizException.of(ResultCode.PARAM_INVALID, "source image index not found");
    }

    private static List<GeneratedImageItem> parseImages(Map<String, Object> result) {
        Object rawImages = result == null ? null : result.get("images");
        if (!(rawImages instanceof List<?> list)) {
            return List.of();
        }
        List<GeneratedImageItem> items = new ArrayList<>();
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> m)) {
                continue;
            }
            Object url = m.get("url");
            Object index = m.get("index");
            if (url == null || index == null) {
                continue;
            }
            try {
                int idx = ((Number) index).intValue();
                items.add(new GeneratedImageItem(idx, String.valueOf(url), strOr(m.get("prompt"), "")));
            } catch (RuntimeException ignore) {
                // 跳过畸形项
            }
        }
        items.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return items;
    }

    private static int resolveCount(Integer count) {
        int value = count == null ? MIN_COUNT : count;
        return resolveCount(value);
    }

    private static int resolveCount(int value) {
        if (value < MIN_COUNT || value > MAX_COUNT) {
            throw BizException.of(ResultCode.PARAM_INVALID, "count must be between 1 and 5");
        }
        return value;
    }

    private static int resolveRevisionCount(
            Integer explicit, Map<String, Object> sourceResult, Map<String, Object> sourcePayload) {
        if (explicit != null) {
            return resolveCount(explicit);
        }
        int fromSource = intOr(sourceResult.get("count"), intOr(sourcePayload.get("count"), 1));
        return resolveCount(fromSource);
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

    private static String cleanBounded(Object value, int maxLen) {
        String text = clean(value);
        if (text == null) {
            return null;
        }
        if (text.length() > maxLen) {
            throw BizException.of(ResultCode.PARAM_INVALID, "value exceeds max length " + maxLen);
        }
        return text;
    }

    private static String cleanMode(Object value) {
        String text = clean(value);
        if (text == null) {
            return null;
        }
        return switch (text) {
            case MODE_EDIT, MODE_GENERATE, MODE_FROM_IMAGE -> text;
            default -> null;
        };
    }

    private static String requiredText(Object value, String fieldName) {
        String text = clean(value);
        if (text == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " is required");
        }
        return text;
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            String text = clean(v);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String strOr(Object value, Object fallback) {
        String text = clean(value);
        return text == null ? clean(fallback) : text;
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean boolOf(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
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
}
