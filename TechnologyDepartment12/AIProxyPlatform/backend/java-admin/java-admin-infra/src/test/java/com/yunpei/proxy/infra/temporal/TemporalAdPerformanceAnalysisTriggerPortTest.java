package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.infra.temporal.workflow.ad.AdPerformanceAnalysisWorkflowImpl;
import com.yunpei.proxy.service.port.AdPerformanceAnalysisTriggerPort;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link TemporalAdPerformanceAnalysisTriggerPort} 单元测试。
 */
class TemporalAdPerformanceAnalysisTriggerPortTest {

    private final WorkflowClient workflowClient = mock(WorkflowClient.class);
    private final WorkflowStub workflowStub = mock(WorkflowStub.class);
    private TemporalAdPerformanceAnalysisTriggerPort port;

    @BeforeEach
    void setUp() {
        port = new TemporalAdPerformanceAnalysisTriggerPort(workflowClient);
    }

    @Test
    void start_startsAdPerformanceWorkflow() {
        when(workflowClient.newUntypedWorkflowStub(
                        eq(TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any()))
                .thenReturn(WorkflowExecution.newBuilder()
                        .setWorkflowId("wf-ad_performance_analysis-ana_1")
                        .setRunId("run-1")
                        .build());

        var started = port.start("ana_1");

        assertThat(started.workflowId()).isEqualTo("wf-ad_performance_analysis-ana_1");
        ArgumentCaptor<WorkflowOptions> optionsCap = ArgumentCaptor.forClass(WorkflowOptions.class);
        verify(workflowClient)
                .newUntypedWorkflowStub(eq(TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS), optionsCap.capture());
        WorkflowOptions options = optionsCap.getValue();
        assertThat(options.getTaskQueue()).isEqualTo(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS);
        assertThat(options.getWorkflowId()).isEqualTo("wf-ad_performance_analysis-ana_1");
        assertThat(options.getWorkflowExecutionTimeout())
                .isEqualTo(Duration.ofSeconds(TemporalAdPerformanceAnalysisTriggerPort.TIMEOUT_SECONDS));
        assertThat(options.getMemo())
                .containsEntry(TemporalTaskTriggerPort.MEMO_CONFIG_CODE, AdPerformanceAnalysisTriggerPort.CONFIG_CODE);

        ArgumentCaptor<Object> inputCap = ArgumentCaptor.forClass(Object.class);
        verify(workflowStub).start(inputCap.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) inputCap.getValue();
        assertThat(input).containsEntry(AdPerformanceAnalysisWorkflowImpl.INPUT_JOB_ID, "ana_1");
    }

    @Test
    void start_rejectsBlankId() {
        assertThatThrownBy(() -> port.start("  "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("jobId");
    }

    @Test
    void start_wrapsClientFailure() {
        when(workflowClient.newUntypedWorkflowStub(
                        eq(TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any())).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.start("ana_x"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Temporal start failed");
    }
}
