package com.yunpei.proxy.service.externalimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.image.ImageProviderFactory;
import com.yunpei.proxy.service.image.ImageProviderProperties;
import com.yunpei.proxy.service.image.PlaceholderImageStorage;
import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link ExternalImageGenerationService} 契约测试：持久化 / prompt 校验 / external_request_id 幂等 / 触发 / 执行记录。
 */
class ExternalImageGenerationServiceTest {

    private final GenerationTaskRepository repository = mock(GenerationTaskRepository.class);
    private final ImageGenerationTriggerPort triggerPort = mock(ImageGenerationTriggerPort.class);
    private final TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private final PlaceholderImageStorage imageStorage = new PlaceholderImageStorage();
    private final ExternalImageGenerationService service = new ExternalImageGenerationService(
            repository,
            new ImageProviderFactory(),
            imageStorage,
            triggerPort,
            executionRepository,
            configRepository,
            new ImageProviderProperties());

    @Test
    void createJob_persistsProcessingTaskAndExecutionSeed() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-image_generation-job-x", "run-1"));
        when(configRepository.findByCode(ImageGenerationTriggerPort.CONFIG_CODE))
                .thenReturn(null);

        CreateJobResult result = service.createJob(CreateImageGenerationCommand.of(null, "a cat", null, null, null));

        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.size()).isEqualTo("9:16");
        assertThat(result.jobId()).isNotBlank();

        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        GenerationTask row = captor.getValue();
        assertThat(row.getId()).isEqualTo(result.jobId());
        assertThat(row.getStatus()).isEqualTo("processing");
        assertThat(row.getTaskType()).isEqualTo("external_image_generate");
        assertThat(row.getBusinessType()).isEqualTo("external_image");
        assertThat(row.getBusinessId()).isEqualTo(row.getId()); // 无 external_request_id → business_id = job_id
        assertThat(row.getPayloadJson()).contains("a cat");
        assertThat(row.getAttemptCount()).isZero();

        // 落库后触发 Temporal workflow 执行（T3）
        verify(triggerPort).start(result.jobId());

        ArgumentCaptor<TemporalTaskExecution> execCap = ArgumentCaptor.forClass(TemporalTaskExecution.class);
        verify(executionRepository).insert(execCap.capture());
        TemporalTaskExecution exec = execCap.getValue();
        assertThat(exec.getConfigId()).isNull();
        assertThat(exec.getWorkflowId()).isEqualTo("wf-image_generation-job-x");
        assertThat(exec.getRunId()).isEqualTo("run-1");
        assertThat(exec.getWorkflowType()).isEqualTo(TemporalWorkflowType.IMAGE_GENERATION);
        assertThat(exec.getTaskQueue()).isEqualTo(TemporalTaskQueue.IMAGE_GENERATION);
        // 种子行为 PENDING；终态由 ExecutionMirrorTick 对账推进
        assertThat(exec.getStatus()).isEqualTo("PENDING");
        assertThat(exec.getRetryCount()).isZero();
        assertThat(exec.getInputSummary()).contains(result.jobId());
        assertThat(exec.getInputSummary()).contains(ImageGenerationTriggerPort.CONFIG_CODE);
        assertThat(exec.getStartedAt()).isNull();
        assertThat(exec.getPendingAt()).isNotNull();
        assertThat(exec.getCreatedAt()).isNotNull();
    }

    @Test
    void createJob_linksConfigIdWhenImageGenerationConfigExists() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-image_generation-1", "run-2"));
        TemporalTaskConfig config = new TemporalTaskConfig();
        config.setId(99L);
        config.setCode(ImageGenerationTriggerPort.CONFIG_CODE);
        when(configRepository.findByCode(ImageGenerationTriggerPort.CONFIG_CODE))
                .thenReturn(config);

        CreateJobResult result = service.createJob(CreateImageGenerationCommand.of(null, "a dog", null, null, null));

        ArgumentCaptor<TemporalTaskExecution> execCap = ArgumentCaptor.forClass(TemporalTaskExecution.class);
        verify(executionRepository).insert(execCap.capture());
        assertThat(execCap.getValue().getConfigId()).isEqualTo(99L);
        assertThat(result.jobId()).isNotBlank();
    }

    @Test
    void createJob_skipsExecutionSeedWhenTriggerReturnsNull() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(null);

        CreateJobResult result = service.createJob(CreateImageGenerationCommand.of(null, "a cat", null, null, null));

        assertThat(result.status()).isEqualTo("processing");
        verify(triggerPort).start(result.jobId());
        verify(executionRepository, never()).insert(any());
    }

    @Test
    void createJob_blankPrompt_throwsAndDoesNotInsert() {
        assertThatThrownBy(() -> service.createJob(CreateImageGenerationCommand.of(null, "   ", null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessage("prompt is required");
        verify(repository, never()).insert(ArgumentMatchers.any());
        verify(executionRepository, never()).insert(any());
    }

    @Test
    void createJob_countOutOfRange_throws() {
        assertThatThrownBy(() -> service.createJob(CreateImageGenerationCommand.of(null, "a cat", 6, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("count");
        verify(repository, never()).insert(ArgumentMatchers.any());
        verify(executionRepository, never()).insert(any());
    }

    @Test
    void createJob_sameExternalRequestId_reusesExistingWithoutInsert() {
        GenerationTask existing = new GenerationTask();
        existing.setId("job-1");
        existing.setStatus("processing");
        existing.setPayloadJson("{\"count\":2,\"size\":\"1:1\"}");
        when(repository.findByExternalRequestIdAndType("req-1", "external_image_generate", "external_image"))
                .thenReturn(existing);

        CreateJobResult result = service.createJob(CreateImageGenerationCommand.of("req-1", "a cat", 1, null, null));

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.count()).isEqualTo(2); // 复用既有 payload
        verify(repository, never()).insert(ArgumentMatchers.any());
        verify(triggerPort, never()).start(ArgumentMatchers.any()); // 幂等复用不重复触发
        verify(executionRepository, never()).insert(any());
    }

    @Test
    void getJob_notFound_throwsNotFound() {
        when(repository.findById("nope")).thenReturn(null);
        assertThatThrownBy(() -> service.getJob("nope")).isInstanceOf(GenerationTaskNotFoundException.class);
    }

    @Test
    void getJob_processing_returnsView() {
        GenerationTask task = new GenerationTask();
        task.setId("job-9");
        task.setTaskType("external_image_generate");
        task.setStatus("processing");
        task.setPayloadJson("{\"prompt\":\"a cat\",\"count\":3,\"size\":\"1:1\",\"model_id\":\"m1\"}");
        when(repository.findById("job-9")).thenReturn(task);

        GenerationJobView view = service.getJob("job-9");

        assertThat(view.jobId()).isEqualTo("job-9");
        assertThat(view.status()).isEqualTo("processing");
        assertThat(view.count()).isEqualTo(3);
        assertThat(view.size()).isEqualTo("1:1");
        assertThat(view.modelId()).isEqualTo("m1");
        assertThat(view.images()).isEmpty();
        assertThat(view.error()).isNull();
    }

    @Test
    void getJob_succeeded_returnsImages() {
        GenerationTask task = new GenerationTask();
        task.setId("job-10");
        task.setTaskType("external_image_generate");
        task.setStatus("succeeded");
        task.setPayloadJson("{\"prompt\":\"a cat\",\"count\":1,\"size\":\"9:16\",\"model_id\":\"m1\"}");
        task.setResultJson(
                "{\"images\":[{\"index\":1,\"url\":\"https://img.example/generated/x.png\",\"prompt\":\"a cat\","
                        + "\"size\":\"9:16\"}],\"count\":1,\"size\":\"9:16\",\"model_id\":\"m1\"}");
        when(repository.findById("job-10")).thenReturn(task);

        GenerationJobView view = service.getJob("job-10");

        assertThat(view.status()).isEqualTo("succeeded");
        assertThat(view.count()).isEqualTo(1);
        assertThat(view.size()).isEqualTo("9:16");
        assertThat(view.images()).hasSize(1);
        assertThat(view.images().get(0).index()).isEqualTo(1);
        assertThat(view.images().get(0).url()).isEqualTo("https://img.example/generated/x.png");
        assertThat(view.error()).isNull();
    }

    @Test
    void createEditJob_persistsFromImageModeAndStorageKey() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-edit", "run-e"));

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        CreateJobResult result = service.createEditJob(
                new CreateImageEditCommand(png, "image/png", "Make brighter", 1, "1:1", "m-edit", "edit-req-1"));

        assertThat(result.mode()).isEqualTo("from_image");
        assertThat(result.size()).isEqualTo("1:1");
        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        GenerationTask row = captor.getValue();
        assertThat(row.getPayloadJson()).contains("from_image");
        assertThat(row.getPayloadJson()).contains("source_storage_key");
        assertThat(row.getPayloadJson()).doesNotContain("reference_image_data_url");
        assertThat(row.getMetadataJson()).contains("external_image_edit");
        verify(triggerPort).start(result.jobId());
    }

    @Test
    void createEditJob_rejectsUnsupportedContentType() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThatThrownBy(() ->
                        service.createEditJob(new CreateImageEditCommand(png, "text/plain", "x", 1, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Unsupported image content type");
        verify(repository, never()).insert(any());
    }

    @Test
    void createRevisionJob_fromSucceededSource() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-rev", "run-r"));

        var transfer = imageStorage.storeUploadedSourceImage(
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "image/png");

        GenerationTask source = new GenerationTask();
        source.setId("src-1");
        source.setTaskType("external_image_generate");
        source.setStatus("succeeded");
        source.setPayloadJson("{\"prompt\":\"Original prompt\",\"count\":1,\"size\":\"4:5\",\"model_id\":\"m1\"}");
        source.setResultJson(
                "{\"images\":[{\"index\":1,\"url\":\"https://img.example/s.png\",\"storage_key\":\""
                        + transfer.storageKey()
                        + "\",\"prompt\":\"Original prompt\",\"size\":\"4:5\"}],\"count\":1,\"size\":\"4:5\",\"model_id\":\"m1\"}");
        when(repository.findById("src-1")).thenReturn(source);

        CreateJobResult result = service.createRevisionJob(
                new CreateImageRevisionCommand("src-1", "把背景改成红色", 1, null, null, null, "rev-1"));

        assertThat(result.sourceJobId()).isEqualTo("src-1");
        assertThat(result.mode()).isIn("generate", "edit");
        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains("is_revision");
        assertThat(captor.getValue().getPayloadJson()).contains("revised_prompt");
        assertThat(captor.getValue().getPayloadJson()).doesNotContain("reference_image_data_url");
        assertThat(captor.getValue().getPayloadJson()).contains(transfer.storageKey());
    }

    @Test
    void createRevisionJob_missingStorageKey_throws() {
        GenerationTask source = new GenerationTask();
        source.setId("src-2");
        source.setTaskType("external_image_generate");
        source.setStatus("succeeded");
        source.setPayloadJson("{\"prompt\":\"p\",\"count\":1}");
        source.setResultJson(
                "{\"images\":[{\"index\":1,\"url\":\"https://img.example/s.png\",\"prompt\":\"p\"}],\"count\":1}");
        when(repository.findById("src-2")).thenReturn(source);

        assertThatThrownBy(() -> service.createRevisionJob(
                        new CreateImageRevisionCommand("src-2", "改", 1, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("storage_key");
        verify(repository, never()).insert(any());
    }

    @Test
    void execute_generate_writesStorageKey() {
        GenerationTask task = new GenerationTask();
        task.setId("job-exec");
        task.setStatus("processing");
        task.setPayloadJson("{\"prompt\":\"a cat\",\"count\":1,\"size\":\"9:16\"}");
        when(repository.findById("job-exec")).thenReturn(task);

        var result = service.execute("job-exec");

        assertThat(result.get("images")).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        var images = (java.util.List<java.util.Map<String, Object>>) result.get("images");
        assertThat(images).hasSize(1);
        assertThat(images.get(0).get("storage_key")).isNotNull();
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-exec"), ArgumentMatchers.contains("storage_key"));
    }
}
