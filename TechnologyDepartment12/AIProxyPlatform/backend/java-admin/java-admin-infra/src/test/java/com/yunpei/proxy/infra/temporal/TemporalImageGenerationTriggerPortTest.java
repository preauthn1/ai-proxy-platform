package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.infra.temporal.workflow.image.ImageGenerationWorkflowImpl;
import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
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
 * {@link TemporalImageGenerationTriggerPort} 单元测试（mock {@link WorkflowClient}）。
 *
 * <p>对齐 {@link TemporalTaskTriggerPort}：直接启动业务 ImageGenerationWorkflow，不再经 JobDispatch。
 */
class TemporalImageGenerationTriggerPortTest {

    private final WorkflowClient workflowClient = mock(WorkflowClient.class);
    private final WorkflowStub workflowStub = mock(WorkflowStub.class);
    private TemporalImageGenerationTriggerPort port;

    @BeforeEach
    void setUp() {
        port = new TemporalImageGenerationTriggerPort(workflowClient);
    }

    @Test
    void start_startsImageGenerationWorkflowDirectly() {
        when(workflowClient.newUntypedWorkflowStub(
                        eq(TemporalWorkflowType.IMAGE_GENERATION), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any()))
                .thenReturn(WorkflowExecution.newBuilder()
                        .setWorkflowId("wf-image_generation-job-1")
                        .setRunId("run-img-1")
                        .build());

        var started = port.start("job-1");

        assertThat(started).isNotNull();
        assertThat(started.workflowId()).isEqualTo("wf-image_generation-job-1");
        assertThat(started.runId()).isEqualTo("run-img-1");

        ArgumentCaptor<WorkflowOptions> optionsCap = ArgumentCaptor.forClass(WorkflowOptions.class);
        verify(workflowClient).newUntypedWorkflowStub(eq(TemporalWorkflowType.IMAGE_GENERATION), optionsCap.capture());
        WorkflowOptions options = optionsCap.getValue();
        assertThat(options.getTaskQueue()).isEqualTo(TemporalTaskQueue.IMAGE_GENERATION);
        assertThat(options.getWorkflowId()).isEqualTo("wf-image_generation-job-1");
        assertThat(options.getWorkflowExecutionTimeout())
                .isEqualTo(Duration.ofSeconds(TemporalImageGenerationTriggerPort.TIMEOUT_SECONDS));
        assertThat(options.getMemo())
                .containsEntry(TemporalTaskTriggerPort.MEMO_CONFIG_CODE, ImageGenerationTriggerPort.CONFIG_CODE);
        assertThat(options.getMemo()).doesNotContainKey(TemporalTaskTriggerPort.MEMO_CONFIG_ID);

        ArgumentCaptor<Object> inputCap = ArgumentCaptor.forClass(Object.class);
        verify(workflowStub).start(inputCap.capture());
        assertThat(inputCap.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) inputCap.getValue();
        assertThat(input).containsEntry(ImageGenerationWorkflowImpl.INPUT_JOB_ID, "job-1");
    }

    @Test
    void start_rejectsBlankJobId() {
        assertThatThrownBy(() -> port.start("  "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("jobId");
    }

    @Test
    void start_wrapsClientFailure() {
        when(workflowClient.newUntypedWorkflowStub(
                        eq(TemporalWorkflowType.IMAGE_GENERATION), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any())).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.start("job-x"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Temporal start failed");
    }
}
