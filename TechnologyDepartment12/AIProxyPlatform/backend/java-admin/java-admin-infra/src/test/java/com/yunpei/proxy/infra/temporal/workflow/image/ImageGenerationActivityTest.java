package com.yunpei.proxy.infra.temporal.workflow.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.externalimage.ExternalImageGenerationService;
import com.yunpei.proxy.service.image.ImageBrief;
import com.yunpei.proxy.service.image.ImageProvider;
import com.yunpei.proxy.service.image.ImageProviderFactory;
import com.yunpei.proxy.service.image.PlaceholderImageStorage;
import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link ImageGenerationActivity} / 执行管线契约测试（T3）。
 *
 * <p>覆盖：placeholder 下 job → {@code succeeded} + 假 URL 回写；失败 → {@code failed} + error_code；
 * 幂等（非 processing 不重复执行）；activity 委托 service。
 */
class ImageGenerationActivityTest {

    private final GenerationTaskRepository repository = mock(GenerationTaskRepository.class);

    private ExternalImageGenerationService serviceWith(ImageProviderFactory providerFactory) {
        return new ExternalImageGenerationService(
                repository,
                providerFactory,
                new PlaceholderImageStorage(),
                mock(ImageGenerationTriggerPort.class),
                mock(TemporalTaskExecutionRepository.class),
                mock(TemporalTaskConfigRepository.class),
                new com.yunpei.proxy.service.image.ImageProviderProperties());
    }

    @Test
    void execute_placeholderJob_marksSucceededWithFakeUrl() {
        GenerationTask task = processingTask("job-1", 2, "9:16");
        when(repository.findById("job-1")).thenReturn(task);

        var returned = serviceWith(new ImageProviderFactory()).execute("job-1");

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-1"), resultCaptor.capture());
        verify(repository, never()).markFailed(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        String resultJson = resultCaptor.getValue();
        assertThat(resultJson).contains("https://img.example/generated/");
        assertThat(resultJson).contains("\"count\":2");
        assertThat(resultJson).contains("\"size\":\"9:16\"");
        assertThat(returned).isNotNull();
        assertThat(returned.get("count")).isEqualTo(2);
        assertThat(returned.get("jobId")).isEqualTo("job-1");
        assertThat(returned.get("images")).isInstanceOf(java.util.List.class);
    }

    @Test
    void execute_alreadySucceeded_doesNotRewrite_returnsExistingResult() {
        GenerationTask task = processingTask("job-2", 1, "9:16");
        task.setStatus("succeeded");
        task.setResultJson("{\"images\":[],\"count\":1,\"size\":\"9:16\",\"jobId\":\"job-2\"}");
        when(repository.findById("job-2")).thenReturn(task);

        var returned = serviceWith(new ImageProviderFactory()).execute("job-2");

        verify(repository, never()).markSucceeded(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(repository, never()).markFailed(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        assertThat(returned).containsEntry("count", 1);
        assertThat(returned).containsEntry("jobId", "job-2");
    }

    @Test
    void execute_providerThrows_marksFailedWithErrorCode() {
        GenerationTask task = processingTask("job-3", 1, "9:16");
        when(repository.findById("job-3")).thenReturn(task);

        ImageProvider failingProvider = mock(ImageProvider.class);
        when(failingProvider.generate(ArgumentMatchers.any(ImageBrief.class)))
                .thenThrow(new IllegalStateException("boom"));
        ImageProviderFactory factory = mock(ImageProviderFactory.class);
        when(factory.provider()).thenReturn(failingProvider);

        var returned = serviceWith(factory).execute("job-3");

        verify(repository, never()).markSucceeded(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(repository)
                .markFailed(
                        ArgumentMatchers.eq("job-3"),
                        ArgumentMatchers.eq("provider_error"),
                        ArgumentMatchers.contains("boom"));
        assertThat(returned).containsEntry("status", "failed");
        assertThat(returned).containsEntry("error_code", "provider_error");
        assertThat(String.valueOf(returned.get("error_message"))).contains("boom");
    }

    @Test
    void activityImpl_delegatesToService() {
        ExternalImageGenerationService service = mock(ExternalImageGenerationService.class);
        when(service.execute("job-4")).thenReturn(java.util.Map.of("jobId", "job-4", "count", 1));
        ImageGenerationActivity activity = new ImageGenerationActivityImpl(service);

        var returned = activity.execute("job-4");

        verify(service).execute("job-4");
        assertThat(returned).containsEntry("jobId", "job-4");
    }

    @Test
    void execute_jobNotFound_throwsAndMarksNothing() {
        when(repository.findById("nope")).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> serviceWith(new ImageProviderFactory()).execute("nope"))
                .isInstanceOf(com.yunpei.proxy.service.externalimage.GenerationTaskNotFoundException.class);
        verify(repository, never()).markSucceeded(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(repository, never()).markFailed(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private static GenerationTask processingTask(String id, int count, String size) {
        GenerationTask task = new GenerationTask();
        task.setId(id);
        task.setTaskType("external_image_generate");
        task.setBusinessType("external_image");
        task.setStatus("processing");
        task.setPayloadJson("{\"prompt\":\"a cat\",\"count\":" + count + ",\"size\":\"" + size + "\"}");
        return task;
    }
}
