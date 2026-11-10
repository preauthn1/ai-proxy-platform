package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerRequest;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link TemporalTaskTriggerPort} 单元测试（mock {@link WorkflowClient}）。
 */
class TemporalTaskTriggerPortTest {

    private final WorkflowClient workflowClient = mock(WorkflowClient.class);
    private final WorkflowStub workflowStub = mock(WorkflowStub.class);
    private TemporalTaskTriggerPort port;

    @BeforeEach
    void setUp() {
        port = new TemporalTaskTriggerPort(workflowClient);
    }

    @Test
    void start_usesBusinessWorkflowAndMemo() {
        when(workflowClient.newUntypedWorkflowStub(eq("LogCountTickWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any()))
                .thenReturn(WorkflowExecution.newBuilder()
                        .setWorkflowId("wf-log_count_tick-1")
                        .setRunId("run-abc")
                        .build());

        TriggerResult result = port.start(new TriggerRequest(
                1L,
                "log_count_tick",
                "LogCountTickWorkflow",
                "demo",
                null,
                Map.of("maxAttempts", 3, "initialInterval", "30s", "backoff", 2.0),
                3600,
                Map.of("trigger", "manual", "configCode", "log_count_tick")));

        assertThat(result.workflowId()).isEqualTo("wf-log_count_tick-1");
        assertThat(result.runId()).isEqualTo("run-abc");

        ArgumentCaptor<WorkflowOptions> optionsCap = ArgumentCaptor.forClass(WorkflowOptions.class);
        verify(workflowClient).newUntypedWorkflowStub(eq("LogCountTickWorkflow"), optionsCap.capture());
        WorkflowOptions options = optionsCap.getValue();
        assertThat(options.getTaskQueue()).isEqualTo("demo");
        assertThat(options.getWorkflowId()).startsWith("wf-log_count_tick-");
        assertThat(options.getWorkflowExecutionTimeout()).isEqualTo(Duration.ofHours(1));
        assertThat(options.getRetryOptions()).isNotNull();
        assertThat(options.getRetryOptions().getMaximumAttempts()).isEqualTo(3);
        assertThat(options.getMemo()).containsEntry(TemporalTaskTriggerPort.MEMO_CONFIG_ID, 1L);
        assertThat(options.getMemo()).containsEntry(TemporalTaskTriggerPort.MEMO_CONFIG_CODE, "log_count_tick");

        ArgumentCaptor<Object> inputCap = ArgumentCaptor.forClass(Object.class);
        verify(workflowStub).start(inputCap.capture());
        assertThat(inputCap.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) inputCap.getValue();
        assertThat(input).containsEntry("trigger", "manual");
    }

    @Test
    void start_rejectsBlankWorkflowType() {
        assertThatThrownBy(() -> port.start(new TriggerRequest(1L, "c", " ", "q", null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("workflowType");
    }

    @Test
    void start_wrapsClientFailure() {
        when(workflowClient.newUntypedWorkflowStub(eq("LogCountTickWorkflow"), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any())).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.start(new TriggerRequest(
                        1L, "log_count_tick", "LogCountTickWorkflow", "demo", null, null, null, Map.of())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Temporal start failed");
    }

    @Test
    void toRetryOptions_parsesSeedShape() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("maxAttempts", 3);
        policy.put("initialInterval", "30s");
        policy.put("backoff", 2.0);

        RetryOptions options = TemporalTaskTriggerPort.toRetryOptions(policy);

        assertThat(options).isNotNull();
        assertThat(options.getMaximumAttempts()).isEqualTo(3);
        assertThat(options.getInitialInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getBackoffCoefficient()).isEqualTo(2.0);
    }

    @Test
    void parseDuration_supportsCommonSuffixes() {
        assertThat(TemporalTaskTriggerPort.parseDuration("30s")).isEqualTo(Duration.ofSeconds(30));
        assertThat(TemporalTaskTriggerPort.parseDuration("2m")).isEqualTo(Duration.ofMinutes(2));
        assertThat(TemporalTaskTriggerPort.parseDuration(45)).isEqualTo(Duration.ofSeconds(45));
        assertThat(TemporalTaskTriggerPort.parseDuration("PT10S")).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void parseConfigCodeFromWorkflowId_supportsManualAndSchedule() {
        assertThat(TemporalTaskTriggerPort.parseConfigCodeFromWorkflowId("wf-log_count_tick-20240101120000-1"))
                .isEqualTo("log_count_tick");
        assertThat(TemporalTaskTriggerPort.parseConfigCodeFromWorkflowId("sched-log_count_tick"))
                .isEqualTo("log_count_tick");
        assertThat(TemporalTaskTriggerPort.parseConfigCodeFromWorkflowId("sched-log_count_tick-xyz"))
                .isEqualTo("log_count_tick");
        assertThat(TemporalTaskTriggerPort.parseConfigCodeFromWorkflowId("other"))
                .isNull();
    }
}
