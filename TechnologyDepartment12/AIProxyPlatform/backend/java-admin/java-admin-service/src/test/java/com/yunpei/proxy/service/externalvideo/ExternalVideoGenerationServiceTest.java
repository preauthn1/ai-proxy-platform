package com.yunpei.proxy.service.externalvideo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.image.PlaceholderImageStorage;
import com.yunpei.proxy.service.llm.LlmProviderFactory;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.port.VideoGenerationTriggerPort;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import com.yunpei.proxy.service.video.PlaceholderVideoStorage;
import com.yunpei.proxy.service.video.VideoProviderFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link ExternalVideoGenerationService} 契约测试。
 */
class ExternalVideoGenerationServiceTest {

    private final GenerationTaskRepository repository = mock(GenerationTaskRepository.class);
    private final VideoGenerationTriggerPort triggerPort = mock(VideoGenerationTriggerPort.class);
    private final TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private final ExternalVideoGenerationService service = new ExternalVideoGenerationService(
            repository,
            new VideoProviderFactory(),
            new PlaceholderImageStorage(),
            new PlaceholderVideoStorage(),
            triggerPort,
            executionRepository,
            configRepository,
            new LlmProviderFactory());

    @Test
    void createJob_persistsProcessingTaskAndExecutionSeed() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-video_generation-job-x", "run-1"));
        when(configRepository.findByCode(VideoGenerationTriggerPort.CONFIG_CODE))
                .thenReturn(null);

        CreateVideoJobResult result =
                service.createJob(CreateVideoGenerationCommand.of(null, List.of(), "scene one", null, null));

        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.durationSeconds()).isEqualTo(12);
        assertThat(result.aspectRatio()).isEqualTo("9:16");
        assertThat(result.jobId()).isNotBlank();

        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        GenerationTask row = captor.getValue();
        assertThat(row.getTaskType()).isEqualTo("external_video_generate");
        assertThat(row.getBusinessType()).isEqualTo("external_video");
        assertThat(row.getPayloadJson()).contains("scene one");
        assertThat(row.getPayloadJson()).contains("text_to_video");

        verify(triggerPort).start(result.jobId());
        ArgumentCaptor<com.yunpei.proxy.service.entity.TemporalTaskExecution> execCap =
                ArgumentCaptor.forClass(com.yunpei.proxy.service.entity.TemporalTaskExecution.class);
        verify(executionRepository).insert(execCap.capture());
        assertThat(execCap.getValue().getWorkflowType()).isEqualTo(TemporalWorkflowType.VIDEO_GENERATION);
        assertThat(execCap.getValue().getTaskQueue()).isEqualTo(TemporalTaskQueue.VIDEO_GENERATION);
    }

    @Test
    void createJob_reusesExistingExternalRequestIdWithoutRetrigger() {
        GenerationTask existing = new GenerationTask();
        existing.setId("existing-job");
        existing.setStatus("processing");
        existing.setPayloadJson("{\"duration_seconds\":8,\"aspect_ratio\":\"16:9\"}");
        when(repository.findByExternalRequestIdAndType(
                        "ext-1",
                        GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE,
                        GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_VIDEO))
                .thenReturn(existing);

        CreateVideoJobResult result =
                service.createJob(CreateVideoGenerationCommand.of("ext-1", List.of(), "scene", 8, "16:9"));

        assertThat(result.jobId()).isEqualTo("existing-job");
        assertThat(result.durationSeconds()).isEqualTo(8);
        verify(repository, never()).insert(any());
        verify(triggerPort, never()).start(any());
    }

    @Test
    void createJob_rejectsInvalidImageCount() {
        assertThatThrownBy(() -> service.createJob(
                        CreateVideoGenerationCommand.of(null, List.of("only-one"), "scene", null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("exactly 2");
    }

    @Test
    void createJob_requiresStoryboardOrProductName() {
        assertThatThrownBy(() -> service.createJob(CreateVideoGenerationCommand.of(null, List.of(), "  ", null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("storyboard_text or product_name");
    }

    @Test
    void createJob_rejectsPromptLongerThan4000() {
        String tooLong = "p".repeat(4001);
        assertThatThrownBy(() -> service.createJob(CreateVideoGenerationCommand.of(
                        null, List.of(), "scene", null, null, null, null, tooLong, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("prompt must be at most 4000");
        verify(repository, never()).insert(any());
    }

    @Test
    void createJob_acceptsPromptOf4000() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-video_generation-job-z", "run-3"));
        when(configRepository.findByCode(VideoGenerationTriggerPort.CONFIG_CODE))
                .thenReturn(null);

        String maxPrompt = "p".repeat(4000);
        CreateVideoJobResult result = service.createJob(
                CreateVideoGenerationCommand.of(null, List.of(), "scene", null, null, null, null, maxPrompt, null));

        assertThat(result.status()).isEqualTo("processing");
        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains(maxPrompt);
    }

    @Test
    void createJob_withProductNameOnly_persistsWithoutStoryboardText() {
        when(repository.findByExternalRequestIdAndType(
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-video_generation-job-y", "run-2"));
        when(configRepository.findByCode(VideoGenerationTriggerPort.CONFIG_CODE))
                .thenReturn(null);

        CreateVideoJobResult result = service.createJob(CreateVideoGenerationCommand.of(
                null, List.of(), null, 12, "9:16", "Demo Product", "energy open", null, null));

        assertThat(result.status()).isEqualTo("processing");
        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains("Demo Product");
        assertThat(captor.getValue().getPayloadJson()).contains("energy open");
        assertThat(captor.getValue().getMetadataJson()).contains("generated");
    }

    @Test
    void startProvider_generatesStoryboardWhenMissing() {
        GenerationTask task = new GenerationTask();
        task.setId("job-gen-sb");
        task.setTaskType(GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE);
        task.setStatus(GenerationTaskRepository.STATUS_PROCESSING);
        task.setPayloadJson(
                "{\"product_name\":\"Demo\",\"duration_seconds\":12,\"aspect_ratio\":\"9:16\",\"images\":[]}");
        task.setMetadataJson("{\"source\":\"external_video_generation\",\"storyboard_source\":\"generated\"}");
        when(repository.findById("job-gen-sb")).thenReturn(task);
        when(repository.updatePayloadJson(any(), any())).thenReturn(1L);
        when(repository.updateMetadataJson(any(), any())).thenReturn(1L);

        var started = service.startProvider("job-gen-sb");
        assertThat(started.get("provider_job_id")).isNotNull();
        assertThat(started.get("status")).isEqualTo("processing");
        verify(repository)
                .updatePayloadJson(ArgumentMatchers.eq("job-gen-sb"), ArgumentMatchers.contains("storyboard_text"));
    }

    @Test
    void getJob_notFound() {
        when(repository.findById("x")).thenReturn(null);
        assertThatThrownBy(() -> service.getJob("x")).isInstanceOf(VideoGenerationTaskNotFoundException.class);
    }

    @Test
    void startAndPoll_placeholderSucceeds() {
        GenerationTask task = new GenerationTask();
        task.setId("job-v");
        task.setTaskType(GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_GENERATE);
        task.setStatus(GenerationTaskRepository.STATUS_PROCESSING);
        task.setPayloadJson(
                "{\"storyboard_text\":\"s\",\"duration_seconds\":12,\"aspect_ratio\":\"9:16\",\"images\":[]}");
        task.setMetadataJson("{\"source\":\"external_video_generation\"}");
        when(repository.findById("job-v")).thenReturn(task);
        when(repository.updateMetadataJson(any(), any())).thenReturn(1L);
        when(repository.markSucceeded(any(), any())).thenAnswer(inv -> {
            task.setStatus("succeeded");
            task.setResultJson(inv.getArgument(1));
            return 1L;
        });

        var started = service.startProvider("job-v");
        assertThat(started.get("provider_job_id")).isNotNull();
        assertThat(started.get("status")).isEqualTo("processing");

        // 模拟 metadata 已写入 provider_job_id
        task.setMetadataJson("{\"source\":\"external_video_generation\",\"provider_job_id\":\""
                + started.get("provider_job_id") + "\"}");

        var polled = service.pollProvider("job-v");
        assertThat(polled.get("url")).isNotNull();
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-v"), any());
    }
}
