package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.temporal.workflow.ai.AiTextGenerationWorkflowImpl;
import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowServiceException;
import io.temporal.client.WorkflowStub;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporal AI 文本生成触发：启动 {@code AiTextGenerationWorkflow}。
 *
 * @author yunpei
 */
public class TemporalAiGenerationTriggerPort implements AiGenerationTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(TemporalAiGenerationTriggerPort.class);

    /** 覆盖 3 次 activity（每次最多 150s）+ 重试间隔。 */
    static final int TIMEOUT_SECONDS = 600;

    private final WorkflowClient workflowClient;

    public TemporalAiGenerationTriggerPort(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public TriggerResult start(String jobId) {
        String safeJobId = requireJobId(jobId);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId("wf-" + CONFIG_CODE + "-" + safeJobId)
                .setTaskQueue(TemporalTaskQueue.AI_TEXT_GENERATION)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .setMemo(TemporalTaskTriggerPort.buildMemo(null, CONFIG_CODE))
                .build();

        Map<String, Object> input = Map.of(AiTextGenerationWorkflowImpl.INPUT_JOB_ID, safeJobId);

        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(TemporalWorkflowType.AI_TEXT_GENERATION, options);
            WorkflowExecution execution = stub.start(input);
            log.info(
                    "AI text generation workflow started: jobId={} workflowId={} runId={} type={} queue={}",
                    safeJobId,
                    execution.getWorkflowId(),
                    execution.getRunId(),
                    TemporalWorkflowType.AI_TEXT_GENERATION,
                    TemporalTaskQueue.AI_TEXT_GENERATION);
            return new TriggerResult(execution.getWorkflowId(), execution.getRunId());
        } catch (WorkflowServiceException ex) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed: " + ex.getMessage());
        }
    }

    private static String requireJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "jobId is required");
        }
        return jobId.trim();
    }
}
