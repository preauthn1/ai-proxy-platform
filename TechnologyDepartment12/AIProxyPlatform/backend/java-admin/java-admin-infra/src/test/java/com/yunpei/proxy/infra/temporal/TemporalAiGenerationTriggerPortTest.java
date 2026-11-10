package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.infra.temporal.workflow.ai.AiTextGenerationWorkflowImpl;
import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
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
 * {@link TemporalAiGenerationTriggerPort} 单元测试（Seam S5：Trigger 启动参数）。
 */
class TemporalAiGenerationTriggerPortTest {

    private final WorkflowClient workflowClient = mock(WorkflowClient.class);
    private final WorkflowStub workflowStub = mock(WorkflowStub.class);
    private TemporalAiGenerationTriggerPort port;

    @BeforeEach
    void setUp() {
        port = new TemporalAiGenerationTriggerPort(workflowClient);
    }

    @Test
    void start_startsAiTextGenerationWorkflowDirectly() {
        when(workflowClient.newUntypedWorkflowStub(
                        eq(TemporalWorkflowType.AI_TEXT_GENERATION), any(WorkflowOptions.class)))
                .thenReturn(workflowStub);
        when(workflowStub.start(any()))
                .thenReturn(WorkflowExecution.newBuilder()
                        .setWorkflowId("wf-ai_text_generation-job-1")
                        .setRunId("run-ai-1")
                        .build());

        var started = port.start("job-1");

        assertThat(started).isNotNull();
        assertThat(started.workflowId()).isEqualTo("wf-ai_text_generation-job-1");
        assertThat(started.runId()).isEqualTo("run-ai-1");

        ArgumentCaptor<WorkflowOptions> optionsCap = ArgumentCaptor.forClass(WorkflowOptions.class);
        verify(workflowClient)
                .newUntypedWorkflowStub(eq(TemporalWorkflowType.AI_TEXT_GENERATION), optionsCap.capture());
        WorkflowOptions options = optionsCap.getValue();
        assertThat(options.getTaskQueue()).isEqualTo(TemporalTaskQueue.AI_TEXT_GENERATION);
        assertThat(options.getWorkflowId()).isEqualTo("wf-ai_text_generation-job-1");
        assertThat(options.getWorkflowExecutionTimeout())
                .isEqualTo(Duration.ofSeconds(TemporalAiGenerationTriggerPort.TIMEOUT_SECONDS));
        assertThat(options.getMemo())
                .containsEntry(TemporalTaskTriggerPort.MEMO_CONFIG_CODE, AiGenerationTriggerPort.CONFIG_CODE);

        ArgumentCaptor<Object> inputCap = ArgumentCaptor.forClass(Object.class);
        verify(workflowStub).start(inputCap.capture());
        assertThat(inputCap.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) inputCap.getValue();
        assertThat(input).containsEntry(AiTextGenerationWorkflowImpl.INPUT_JOB_ID, "job-1");
    }

    @Test
    void start_rejectsBlankJobId() {
        assertThatThrownBy(() -> port.start("  "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("jobId");
    }
}
