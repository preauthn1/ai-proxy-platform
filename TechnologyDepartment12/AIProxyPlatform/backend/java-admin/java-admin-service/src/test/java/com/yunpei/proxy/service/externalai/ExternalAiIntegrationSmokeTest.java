package com.yunpei.proxy.service.externalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.llm.LlmProviderFactory;
import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * 联调冒烟（issue 06）：Mock LLM + Local Trigger 下四类 create → execute → get 可跑通。
 *
 * <p>Local Trigger 返回 null（不写 execution seed、不启 Temporal）；测试内直接调用
 * {@link ExternalAiGenerationService#execute(String)} 模拟 Worker/Activity 侧执行。
 */
class ExternalAiIntegrationSmokeTest {

    private final Map<String, GenerationTask> store = new ConcurrentHashMap<>();
    private final GenerationTaskRepository repository = mock(GenerationTaskRepository.class);
    private final AiGenerationTriggerPort localTrigger = mock(AiGenerationTriggerPort.class);
    private final TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private ExternalAiGenerationService service;

    @BeforeEach
    void setUp() {
        store.clear();
        // LocalAiGenerationTriggerPort 语义：不启动 workflow，返回 null → 不写 execution seed
        when(localTrigger.start(anyString())).thenReturn(null);
        when(configRepository.findByCode(anyString())).thenReturn(null);

        doAnswer(inv -> {
                    GenerationTask row = inv.getArgument(0);
                    store.put(row.getId(), copyOf(row));
                    return null;
                })
                .when(repository)
                .insert(any(GenerationTask.class));
        when(repository.findById(anyString())).thenAnswer(inv -> {
            GenerationTask row = store.get(inv.getArgument(0));
            return row == null ? null : copyOf(row);
        });
        when(repository.findByExternalRequestIdAndType(any(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String externalRequestId = inv.getArgument(0);
                    String taskType = inv.getArgument(1);
                    String businessType = inv.getArgument(2);
                    if (externalRequestId == null || externalRequestId.isBlank()) {
                        return null;
                    }
                    for (GenerationTask row : store.values()) {
                        if (taskType.equals(row.getTaskType())
                                && businessType.equals(row.getBusinessType())
                                && externalRequestId.equals(row.getBusinessId())) {
                            return copyOf(row);
                        }
                    }
                    return null;
                });
        when(repository.markSucceeded(anyString(), anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            String resultJson = inv.getArgument(1);
            GenerationTask row = store.get(id);
            if (row == null || !GenerationTaskRepository.STATUS_PROCESSING.equals(row.getStatus())) {
                return 0L;
            }
            row.setStatus(GenerationTaskRepository.STATUS_SUCCEEDED);
            row.setResultJson(resultJson);
            return 1L;
        });
        when(repository.markFailed(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            GenerationTask row = store.get(id);
            if (row == null || !GenerationTaskRepository.STATUS_PROCESSING.equals(row.getStatus())) {
                return 0L;
            }
            row.setStatus(GenerationTaskRepository.STATUS_FAILED);
            row.setErrorCode(inv.getArgument(1));
            row.setErrorMessage(inv.getArgument(2));
            return 1L;
        });

        service = new ExternalAiGenerationService(
                repository, localTrigger, executionRepository, configRepository, new LlmProviderFactory());
    }

    @Test
    void smoke_workOrderAnalysis_createExecuteGet() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("work_order_type", "game");
        payload.put("media", "fb");
        payload.put("work_order_text", "客户通过 WhatsApp 咨询。日预算 100。投放事件：无事件。");

        CreateAiJobResult created = service.createJob(
                CreateAiJobCommand.of(ExternalAiTaskTypes.WORK_ORDER_ANALYSIS_V3, "smoke-wo-1", payload));
        assertThat(created.status()).isEqualTo("processing");
        verify(localTrigger).start(created.jobId());
        verify(executionRepository, never()).insert(any());

        AiGenerationJobView processing = service.getJob(created.jobId());
        assertThat(processing.status()).isEqualTo("processing");

        Map<String, Object> executed = service.execute(created.jobId());
        assertThat(executed).containsKeys("request_id", "schema_version", "campaign_payload", "review");
        assertThat(executed.get("request_id")).isEqualTo("smoke-wo-1");

        AiGenerationJobView done = service.getJob(created.jobId());
        assertThat(done.status()).isEqualTo("succeeded");
        assertThat(done.result()).containsKeys("schema_version", "detected_objective", "campaign_payload");
        assertThat(done.error()).isNull();
    }

    @Test
    void smoke_copy_createExecuteGet() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_name", "Demo");
        payload.put("count", 2);
        payload.put("customEventType", "PURCHASE");

        CreateAiJobResult created =
                service.createJob(CreateAiJobCommand.of(ExternalAiTaskTypes.COPY_GENERATION, "smoke-copy-1", payload));

        service.execute(created.jobId());
        AiGenerationJobView done = service.getJob(created.jobId());

        assertThat(done.status()).isEqualTo("succeeded");
        assertThat(done.result().get("request_id")).isEqualTo("smoke-copy-1");
        assertThat(done.result().get("copywritings")).asList().hasSize(2);
    }

    @Test
    void smoke_copy_withoutBrief_stillSucceeds() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_name", "Demo");
        payload.put("count", 1);
        payload.put("selling_points", List.of("Clear value", "Fast start"));

        CreateAiJobResult created =
                service.createJob(CreateAiJobCommand.of(ExternalAiTaskTypes.COPY_GENERATION, "smoke-copy-2", payload));
        service.execute(created.jobId());
        AiGenerationJobView done = service.getJob(created.jobId());

        assertThat(done.status()).isEqualTo("succeeded");
        assertThat(done.result().get("copywritings")).asList().hasSize(1);
    }

    @Test
    void smoke_idempotentReuse_sameExternalRequestIdAndTaskType_noSecondTrigger() {
        Map<String, Object> payload = Map.of("product_name", "Demo", "count", 1);
        CreateAiJobResult first =
                service.createJob(CreateAiJobCommand.of(ExternalAiTaskTypes.COPY_GENERATION, "smoke-idem-1", payload));
        CreateAiJobResult second =
                service.createJob(CreateAiJobCommand.of(ExternalAiTaskTypes.COPY_GENERATION, "smoke-idem-1", payload));

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.status()).isEqualTo("processing");
        verify(localTrigger).start(first.jobId());
        verify(localTrigger, org.mockito.Mockito.times(1)).start(ArgumentMatchers.anyString());
        assertThat(store).hasSize(1);
    }

    @Test
    void smoke_sameExternalRequestId_differentTaskType_createsSeparateJobs() {
        CreateAiJobResult workOrder = service.createJob(CreateAiJobCommand.of(
                ExternalAiTaskTypes.WORK_ORDER_ANALYSIS_V3,
                "shared-req",
                Map.of("work_order_text", "客户咨询", "work_order_type", "game", "media", "fb")));
        CreateAiJobResult copy = service.createJob(CreateAiJobCommand.of(
                ExternalAiTaskTypes.COPY_GENERATION, "shared-req", Map.of("product_name", "Demo")));

        assertThat(copy.jobId()).isNotEqualTo(workOrder.jobId());
        assertThat(store).hasSize(2);
    }

    @Test
    void smoke_get_nonTextTaskType_throwsNotFound() {
        GenerationTask image = new GenerationTask();
        image.setId("img-job");
        image.setTaskType(GenerationTaskRepository.TASK_TYPE_EXTERNAL_IMAGE_GENERATE);
        image.setBusinessType(GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_IMAGE);
        image.setStatus(GenerationTaskRepository.STATUS_PROCESSING);
        store.put(image.getId(), image);

        assertThatThrownBy(() -> service.getJob("img-job")).isInstanceOf(AiGenerationTaskNotFoundException.class);
    }

    @Test
    void smoke_get_unknownJob_throwsNotFound() {
        assertThatThrownBy(() -> service.getJob("missing-job")).isInstanceOf(AiGenerationTaskNotFoundException.class);
    }

    private static GenerationTask copyOf(GenerationTask src) {
        GenerationTask copy = new GenerationTask();
        copy.setId(src.getId());
        copy.setTaskType(src.getTaskType());
        copy.setBusinessType(src.getBusinessType());
        copy.setBusinessId(src.getBusinessId());
        copy.setStatus(src.getStatus());
        copy.setPayloadJson(src.getPayloadJson());
        copy.setResultJson(src.getResultJson());
        copy.setMetadataJson(src.getMetadataJson());
        copy.setErrorCode(src.getErrorCode());
        copy.setErrorMessage(src.getErrorMessage());
        copy.setAttemptCount(src.getAttemptCount());
        return copy;
    }
}
