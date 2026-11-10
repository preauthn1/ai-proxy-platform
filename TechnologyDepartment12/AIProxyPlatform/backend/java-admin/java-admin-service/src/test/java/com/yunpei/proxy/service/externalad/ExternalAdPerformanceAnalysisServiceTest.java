package com.yunpei.proxy.service.externalad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.GenerationTask;
import com.yunpei.proxy.service.port.AdPerformanceAnalysisTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ExternalAdPerformanceAnalysisService}：落库 / 幂等 / 503 重试 / GET。
 */
class ExternalAdPerformanceAnalysisServiceTest {

    private final GenerationTaskRepository repository = mock(GenerationTaskRepository.class);
    private final AdPerformanceAnalysisTriggerPort triggerPort = mock(AdPerformanceAnalysisTriggerPort.class);
    private final TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
    private final TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);
    private final AdAnalysisProperties properties = new AdAnalysisProperties();
    private ExternalAdPerformanceAnalysisService service;

    @BeforeEach
    void init() {
        properties.setPublicBaseUrl("https://ai.ggcss.xyz");
        service = new ExternalAdPerformanceAnalysisService(
                repository, triggerPort, executionRepository, configRepository, properties);
        when(repository.findByExternalRequestIdAndType(any(), any(), any())).thenReturn(null);
        doAnswer(invocation -> {
                    GenerationTask row = invocation.getArgument(0);
                    when(repository.findById(row.getId())).thenReturn(row);
                    return null;
                })
                .when(repository)
                .insert(any());
    }

    @Test
    void createJob_persistsQueuedAndStartsWorkflow() {
        when(triggerPort.start(any())).thenReturn(new TriggerResult("wf-ad_performance_analysis-x", "run-1"));

        CreateAdAnalysisResult result = service.createJob(AdAnalysisRequestValidatorTest.sample());

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.status()).isEqualTo("queued");
        assertThat(result.analysisId()).startsWith("ana_");
        assertThat(result.pollUrl())
                .isEqualTo(
                        "https://ai.ggcss.xyz/api/v1/integrations/ad-performance/analysis-jobs/" + result.analysisId());

        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(repository).insert(captor.capture());
        GenerationTask row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo("queued");
        assertThat(row.getTaskType()).isEqualTo(AdAnalysisTaskTypes.TASK_TYPE);
        assertThat(row.getBusinessId()).isEqualTo("job-1");
        verify(triggerPort).start(result.analysisId());
        verify(executionRepository).insert(any());
    }

    @Test
    void createJob_samePayload_replaysWithoutRetriggerWhenAlreadyDispatched() {
        GenerationTask existing = queuedTask("ana_existing", "job-1", true, null);
        when(repository.findByExternalRequestIdAndType(
                        "job-1", AdAnalysisTaskTypes.TASK_TYPE, AdAnalysisTaskTypes.BUSINESS_TYPE))
                .thenReturn(existing);
        when(repository.findById("ana_existing")).thenReturn(existing);

        CreateAdAnalysisResult result = service.createJob(AdAnalysisRequestValidatorTest.sample());

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.analysisId()).isEqualTo("ana_existing");
        verify(repository, never()).insert(any());
        verify(triggerPort, never()).start(any());
    }

    @Test
    void createJob_sameIdDifferentPayload_conflicts() {
        GenerationTask existing = queuedTask("ana_existing", "job-1", true, null);
        when(repository.findByExternalRequestIdAndType(
                        "job-1", AdAnalysisTaskTypes.TASK_TYPE, AdAnalysisTaskTypes.BUSINESS_TYPE))
                .thenReturn(existing);
        Map<String, Object> changed = AdAnalysisRequestValidatorTest.sample();
        @SuppressWarnings("unchecked")
        Map<String, Object> insight = (Map<String, Object>) changed.get("insight");
        insight.put("spend", "9.99");

        assertThatThrownBy(() -> service.createJob(changed)).isInstanceOf(AdAnalysisIdempotencyConflict.class);
    }

    @Test
    void createJob_dispatchFailure_recordsAndThrows() {
        when(triggerPort.start(any()))
                .thenThrow(BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed"));

        assertThatThrownBy(() -> service.createJob(AdAnalysisRequestValidatorTest.sample()))
                .isInstanceOf(AdAnalysisDispatchException.class)
                .hasMessageContaining("retry the same external_request_id");
        verify(repository).updateMetadataJson(any(), any());
    }

    @Test
    void createJob_replayAfterDispatchFailure_retriggers() {
        GenerationTask existing = queuedTask("ana_retry", "job-1", false, "redis unavailable");
        when(repository.findByExternalRequestIdAndType(
                        "job-1", AdAnalysisTaskTypes.TASK_TYPE, AdAnalysisTaskTypes.BUSINESS_TYPE))
                .thenReturn(existing);
        when(repository.findById("ana_retry")).thenReturn(existing);
        when(triggerPort.start("ana_retry")).thenReturn(new TriggerResult("wf-retry", "run-2"));
        when(configRepository.findByCode(any())).thenReturn(null);

        CreateAdAnalysisResult result = service.createJob(AdAnalysisRequestValidatorTest.sample());

        assertThat(result.idempotentReplay()).isTrue();
        verify(triggerPort).start("ana_retry");
        verify(executionRepository)
                .insert(org.mockito.ArgumentMatchers.argThat(
                        exec -> TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS.equals(exec.getWorkflowType())));
    }

    @Test
    void getJob_missing_throwsNotFound() {
        when(repository.findById("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.getJob("missing")).isInstanceOf(AdAnalysisNotFoundException.class);
    }

    @Test
    void execute_marksProcessing() {
        GenerationTask existing = queuedTask("ana_run", "job-1", true, null);
        when(repository.findById("ana_run")).thenReturn(existing);

        Map<String, Object> result = service.execute("ana_run");

        assertThat(result.get("status")).isEqualTo("processing");
        verify(repository).updateStatus("ana_run", "processing");
        verify(repository).updateMetadataJson(org.mockito.ArgumentMatchers.eq("ana_run"), any());
    }

    private static GenerationTask queuedTask(
            String id, String externalRequestId, boolean dispatched, String dispatchError) {
        Map<String, Object> payload = AdAnalysisRequestValidatorTest.sample();
        AdAnalysisRequestValidator.validate(payload);
        String hash = AdAnalysisPayloadCanonicalizer.payloadHash(payload);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", AdAnalysisTaskTypes.SOURCE);
        metadata.put("external_request_id", externalRequestId);
        metadata.put("payload_hash", hash);
        metadata.put("stage", "queued");
        metadata.put("progress", 0);
        metadata.put("dispatch_attempts", dispatched ? 1 : 1);
        if (dispatched) {
            metadata.put("last_dispatched_at", "2026-08-14T00:00:00Z");
        }
        if (dispatchError != null) {
            metadata.put("dispatch_error", dispatchError);
        }
        GenerationTask row = new GenerationTask();
        row.setId(id);
        row.setTaskType(AdAnalysisTaskTypes.TASK_TYPE);
        row.setBusinessType(AdAnalysisTaskTypes.BUSINESS_TYPE);
        row.setBusinessId(externalRequestId);
        row.setStatus("queued");
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            row.setPayloadJson(mapper.writeValueAsString(payload));
            row.setMetadataJson(mapper.writeValueAsString(metadata));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return row;
    }
}
