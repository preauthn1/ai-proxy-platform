package com.yunpei.proxy.service.externalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.llm.LlmProviderFactory;
import com.yunpei.proxy.service.llm.LlmTimeoutException;
import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link ExternalAiGenerationService} 契约测试（Seam S2）：落库 / 幂等 / execute 回写 / GET 过滤。
 */
class ExternalAiGenerationServiceTest {

    private final GenerationTaskRepository repository = mock(GenerationTaskRepository.class);
    private final AiGenerationTriggerPort triggerPort = mock(AiGenerationTriggerPort.class);
    private final TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private final ExternalAiGenerationService service = new ExternalAiGenerationService(
            repository, triggerPort, executionRepository, configRepository, new LlmProviderFactory());

    @Test
    void createJob_persistsProcessingTaskAndExecutionSeed() {
        when(repository.findByExternalRequestIdAndType(any(), any(), any())).thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-ai_text_generation-job-x", "run-1"));
        when(configRepository.findByCode(AiGenerationTriggerPort.CONFIG_CODE)).thenReturn(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_name", "Demo");
        CreateAiJobResult result =
                service.createJob(CreateAiJobCommand.of(ExternalAiTaskTypes.TOPIC_SELECTION, null, payload));

        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.jobId()).isNotBlank();

        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        GenerationTask row = captor.getValue();
        assertThat(row.getId()).isEqualTo(result.jobId());
        assertThat(row.getStatus()).isEqualTo("processing");
        assertThat(row.getTaskType()).isEqualTo(ExternalAiTaskTypes.TOPIC_SELECTION);
        assertThat(row.getBusinessType()).isEqualTo(ExternalAiTaskTypes.BUSINESS_TYPE);
        assertThat(row.getBusinessId()).isEqualTo(row.getId());
        assertThat(row.getPayloadJson()).contains("Demo");

        verify(triggerPort).start(result.jobId());
        ArgumentCaptor<TemporalTaskExecution> execCap = ArgumentCaptor.forClass(TemporalTaskExecution.class);
        verify(executionRepository).insert(execCap.capture());
        TemporalTaskExecution exec = execCap.getValue();
        assertThat(exec.getWorkflowType()).isEqualTo(TemporalWorkflowType.AI_TEXT_GENERATION);
        assertThat(exec.getTaskQueue()).isEqualTo(TemporalTaskQueue.AI_TEXT_GENERATION);
        assertThat(exec.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createJob_sameExternalRequestId_reusesWithoutRetrigger() {
        GenerationTask existing = new GenerationTask();
        existing.setId("job-existing");
        existing.setStatus("processing");
        when(repository.findByExternalRequestIdAndType(
                        "req-1", ExternalAiTaskTypes.TOPIC_SELECTION, ExternalAiTaskTypes.BUSINESS_TYPE))
                .thenReturn(existing);

        CreateAiJobResult result = service.createJob(
                CreateAiJobCommand.of(ExternalAiTaskTypes.TOPIC_SELECTION, "req-1", Map.of("product_name", "Demo")));

        assertThat(result.jobId()).isEqualTo("job-existing");
        verify(repository, never()).insert(ArgumentMatchers.any());
        verify(triggerPort, never()).start(ArgumentMatchers.any());
        verify(executionRepository, never()).insert(any());
    }

    @Test
    void createJob_blankProductName_throws() {
        assertThatThrownBy(() -> service.createJob(
                        CreateAiJobCommand.of(ExternalAiTaskTypes.TOPIC_SELECTION, null, Map.of("product_name", "  "))))
                .isInstanceOf(BizException.class)
                .hasMessage("product_name is required");
        verify(repository, never()).insert(any());
    }

    @Test
    void createJob_countOutOfRange_throws() {
        assertThatThrownBy(() -> service.createJob(CreateAiJobCommand.of(
                        ExternalAiTaskTypes.TOPIC_SELECTION, null, Map.of("product_name", "Demo", "count", 6))))
                .isInstanceOf(BizException.class)
                .hasMessage("count must be between 1 and 5");
        verify(repository, never()).insert(any());
    }

    @Test
    void createJob_countZero_throws() {
        assertThatThrownBy(() -> service.createJob(CreateAiJobCommand.of(
                        ExternalAiTaskTypes.TOPIC_SELECTION, null, Map.of("product_name", "Demo", "count", 0))))
                .isInstanceOf(BizException.class)
                .hasMessage("count must be between 1 and 5");
    }

    @Test
    void getJob_nonTextTaskType_throwsNotFound() {
        GenerationTask task = new GenerationTask();
        task.setId("img-1");
        task.setTaskType(GenerationTaskRepository.TASK_TYPE_EXTERNAL_IMAGE_GENERATE);
        when(repository.findById("img-1")).thenReturn(task);

        assertThatThrownBy(() -> service.getJob("img-1")).isInstanceOf(AiGenerationTaskNotFoundException.class);
    }

    @Test
    void execute_workOrderAnalysis_marksSucceededWithV3Shape() {
        GenerationTask task = new GenerationTask();
        task.setId("job-wo");
        task.setTaskType(ExternalAiTaskTypes.WORK_ORDER_ANALYSIS_V3);
        task.setStatus("processing");
        task.setPayloadJson("{\"work_order_type\":\"game\",\"media\":\"fb\",\"work_order_text\":"
                + "\"客户通过 WhatsApp 咨询。日预算 100。投放事件：无事件。\","
                + "\"external_request_id\":\"ext-wo-1\"}");
        when(repository.findById("job-wo")).thenReturn(task);

        Map<String, Object> result = service.execute("job-wo");

        assertThat(result.get("request_id")).isEqualTo("ext-wo-1");
        assertThat(result.get("schema_version")).isEqualTo("3.0");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_ENGAGEMENT");
        assertThat(result).containsKeys("campaign_payload", "adset_payload", "creative_payload", "review");
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-wo"), ArgumentMatchers.contains("campaign_payload"));
    }

    @Test
    void execute_topics_marksSucceededWithMockResult() {
        GenerationTask task = new GenerationTask();
        task.setId("job-exec");
        task.setTaskType(ExternalAiTaskTypes.TOPIC_SELECTION);
        task.setStatus("processing");
        task.setPayloadJson(
                "{\"product_name\":\"Demo\",\"count\":2,\"external_request_id\":\"ext-topics-1\",\"country\":\"US\"}");
        when(repository.findById("job-exec")).thenReturn(task);

        Map<String, Object> result = service.execute("job-exec");

        assertThat(result.get("request_id")).isEqualTo("ext-topics-1");
        assertThat(result.get("topics")).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> topics = (java.util.List<Map<String, Object>>) result.get("topics");
        assertThat(topics).hasSize(2);
        assertThat(topics.get(0)).containsKeys("title", "angle", "audience", "selling_points", "rationale");
        assertThat(topics.get(0).get("title").toString()).contains("Demo");
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-exec"), ArgumentMatchers.contains("topics"));
    }

    @Test
    void createJob_copyCountOutOfRange_throws() {
        assertThatThrownBy(() -> service.createJob(CreateAiJobCommand.of(
                        ExternalAiTaskTypes.COPY_GENERATION, null, Map.of("product_name", "Demo", "count", 6))))
                .isInstanceOf(BizException.class)
                .hasMessage("count must be between 1 and 5");
        verify(repository, never()).insert(any());
    }

    @Test
    void execute_copy_marksSucceededWithMockCopywritings() {
        GenerationTask task = new GenerationTask();
        task.setId("job-copy");
        task.setTaskType(ExternalAiTaskTypes.COPY_GENERATION);
        task.setStatus("processing");
        task.setPayloadJson("{\"product_name\":\"Demo\",\"count\":2,\"external_request_id\":\"ext-copy-1\","
                + "\"country\":\"US\",\"customEventType\":\"PURCHASE\"}");
        when(repository.findById("job-copy")).thenReturn(task);

        Map<String, Object> result = service.execute("job-copy");

        assertThat(result.get("request_id")).isEqualTo("ext-copy-1");
        assertThat(result.get("copywritings")).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> copywritings =
                (java.util.List<Map<String, Object>>) result.get("copywritings");
        assertThat(copywritings).hasSize(2);
        assertThat(copywritings.get(0))
                .containsKeys(
                        "primary_text",
                        "headline",
                        "description",
                        "cta",
                        "call_to_action",
                        "customEventType",
                        "copywriting");
        assertThat(copywritings.get(0).get("primary_text").toString()).contains("Demo");
        assertThat(copywritings.get(0).get("customEventType")).isEqualTo("PURCHASE");
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-copy"), ArgumentMatchers.contains("copywritings"));
    }

    @Test
    void execute_nonProcessing_doesNotRewrite() {
        GenerationTask task = new GenerationTask();
        task.setId("job-done");
        task.setTaskType(ExternalAiTaskTypes.TOPIC_SELECTION);
        task.setStatus("succeeded");
        task.setResultJson("{\"request_id\":\"job-done\",\"topics\":[]}");
        when(repository.findById("job-done")).thenReturn(task);

        Map<String, Object> result = service.execute("job-done");

        assertThat(result.get("request_id")).isEqualTo("job-done");
        verify(repository, never()).markSucceeded(any(), any());
        verify(repository, never()).markFailed(any(), any(), any());
    }

    @Test
    void createJob_storyboard_persistsMetadataWithProfileAndSeed() {
        when(repository.findByExternalRequestIdAndType(any(), any(), any())).thenReturn(null);
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-ai_text_generation-sb", "run-sb"));
        when(configRepository.findByCode(AiGenerationTriggerPort.CONFIG_CODE)).thenReturn(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_name", "Demo");
        payload.put("duration_seconds", 12);
        payload.put("aspect_ratio", "9:16");
        payload.put("image_urls", java.util.List.of("https://cdn.example.com/a.png"));
        CreateAiJobResult result =
                service.createJob(CreateAiJobCommand.of(ExternalAiTaskTypes.VIDEO_STORYBOARD, "req-sb-1", payload));

        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        GenerationTask row = captor.getValue();
        assertThat(row.getTaskType()).isEqualTo(ExternalAiTaskTypes.VIDEO_STORYBOARD);
        assertThat(row.getMetadataJson()).contains("generation_profile");
        assertThat(row.getMetadataJson()).contains("creative_seed");
        assertThat(row.getMetadataJson()).contains("external_storyboard_indian_aaa_v1");
        assertThat(row.getPayloadJson()).contains("https://cdn.example.com/a.png");
        assertThat(result.status()).isEqualTo("processing");
    }

    @Test
    void createJob_storyboardTooManyImageUrls_throws() {
        assertThatThrownBy(() -> service.createJob(CreateAiJobCommand.of(
                        ExternalAiTaskTypes.VIDEO_STORYBOARD,
                        null,
                        Map.of(
                                "product_name",
                                "Demo",
                                "image_urls",
                                java.util.List.of("https://a/1.png", "https://a/2.png", "https://a/3.png")))))
                .isInstanceOf(BizException.class)
                .hasMessage("image_urls must contain at most 2 items");
        verify(repository, never()).insert(any());
    }

    @Test
    void createJob_storyboardDurationOutOfRange_throws() {
        assertThatThrownBy(() -> service.createJob(CreateAiJobCommand.of(
                        ExternalAiTaskTypes.VIDEO_STORYBOARD,
                        null,
                        Map.of("product_name", "Demo", "duration_seconds", 0))))
                .isInstanceOf(BizException.class)
                .hasMessage("duration_seconds must be between 1 and 300");
    }

    @Test
    void execute_storyboard_marksSucceededWithShape() {
        GenerationTask task = new GenerationTask();
        task.setId("job-sb");
        task.setTaskType(ExternalAiTaskTypes.VIDEO_STORYBOARD);
        task.setStatus("processing");
        task.setPayloadJson("{\"product_name\":\"Demo\",\"duration_seconds\":12,\"aspect_ratio\":\"9:16\","
                + "\"image_urls\":[\"https://cdn.example.com/first.png\"],"
                + "\"external_request_id\":\"ext-sb-1\"}");
        task.setMetadataJson("{\"generation_profile\":\"external_storyboard_indian_aaa_v1\","
                + "\"creative_seed\":\"seed-for-redact-test-xyz\"}");
        when(repository.findById("job-sb")).thenReturn(task);

        Map<String, Object> result = service.execute("job-sb");

        assertThat(result.get("request_id")).isEqualTo("ext-sb-1");
        assertThat(result.get("storyboard_text")).asString().isNotBlank();
        assertThat(result.get("duration_seconds")).isEqualTo(12);
        assertThat(result.get("aspect_ratio")).isEqualTo("9:16");
        verify(repository).markSucceeded(ArgumentMatchers.eq("job-sb"), ArgumentMatchers.contains("storyboard_text"));
    }

    @Test
    void execute_storyboardEmptyText_marksFailedProviderError() {
        LlmProviderFactory factory = mock(LlmProviderFactory.class);
        when(factory.provider())
                .thenReturn(request ->
                        com.yunpei.proxy.service.llm.LlmResponse.of("{\"storyboard_text\":\"   \"}", "mock-empty"));
        ExternalAiGenerationService failingService = new ExternalAiGenerationService(
                repository, triggerPort, executionRepository, configRepository, factory);

        GenerationTask task = new GenerationTask();
        task.setId("job-sb-empty");
        task.setTaskType(ExternalAiTaskTypes.VIDEO_STORYBOARD);
        task.setStatus("processing");
        task.setPayloadJson("{\"product_name\":\"Demo\",\"duration_seconds\":12,\"aspect_ratio\":\"9:16\"}");
        when(repository.findById("job-sb-empty")).thenReturn(task);

        Map<String, Object> result = failingService.execute("job-sb-empty");

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("error_code")).isEqualTo(ExternalAiGenerationService.ERROR_CODE_PROVIDER);
        verify(repository)
                .markFailed(
                        ArgumentMatchers.eq("job-sb-empty"),
                        ArgumentMatchers.eq(ExternalAiGenerationService.ERROR_CODE_PROVIDER),
                        ArgumentMatchers.contains("empty"));
        verify(repository, never()).markSucceeded(any(), any());
    }

    @Test
    void execute_llmTimeout_rethrowsWithoutMarkingFailed() {
        LlmProviderFactory factory = mock(LlmProviderFactory.class);
        when(factory.provider()).thenReturn(request -> {
            throw new LlmTimeoutException("openai-compatible call timed out after 120s");
        });
        ExternalAiGenerationService failingService = new ExternalAiGenerationService(
                repository, triggerPort, executionRepository, configRepository, factory);

        GenerationTask task = new GenerationTask();
        task.setId("job-timeout");
        task.setTaskType(ExternalAiTaskTypes.TOPIC_SELECTION);
        task.setStatus("processing");
        task.setPayloadJson("{\"product_name\":\"Demo\"}");
        when(repository.findById("job-timeout")).thenReturn(task);

        assertThatThrownBy(() -> failingService.execute("job-timeout"))
                .isInstanceOf(LlmTimeoutException.class)
                .hasMessageContaining("timed out");
        verify(repository, never()).markFailed(any(), any(), any());
        verify(repository, never()).markSucceeded(any(), any());
    }

    @Test
    void recordProviderFailure_marksFailed() {
        Map<String, Object> result =
                service.recordProviderFailure("job-term", "openai-compatible call timed out after 120s");

        assertThat(result.get("status")).isEqualTo(ExternalAiGenerationService.STATUS_FAILED);
        assertThat(result.get("error_code")).isEqualTo(ExternalAiGenerationService.ERROR_CODE_PROVIDER);
        verify(repository)
                .markFailed(
                        ArgumentMatchers.eq("job-term"),
                        ArgumentMatchers.eq(ExternalAiGenerationService.ERROR_CODE_PROVIDER),
                        ArgumentMatchers.contains("timed out"));
    }
}
