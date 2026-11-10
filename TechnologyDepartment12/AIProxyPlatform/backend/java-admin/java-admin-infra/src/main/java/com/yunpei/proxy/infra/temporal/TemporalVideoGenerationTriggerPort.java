package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.temporal.workflow.video.VideoGenerationWorkflowImpl;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.port.VideoGenerationTriggerPort;
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
 * Temporal 视频生成触发：启动 {@code VideoGenerationWorkflow}。
 *
 * @author yunpei
 */
public class TemporalVideoGenerationTriggerPort implements VideoGenerationTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(TemporalVideoGenerationTriggerPort.class);

    /** 与 Workflow 轮询总时长对齐（30 分钟）。 */
    static final int TIMEOUT_SECONDS = 1800;

    private final WorkflowClient workflowClient;

    public TemporalVideoGenerationTriggerPort(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public TriggerResult start(String jobId) {
        String safeJobId = requireJobId(jobId);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId("wf-" + CONFIG_CODE + "-" + safeJobId)
                .setTaskQueue(TemporalTaskQueue.VIDEO_GENERATION)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .setMemo(TemporalTaskTriggerPort.buildMemo(null, CONFIG_CODE))
                .build();

        Map<String, Object> input = Map.of(VideoGenerationWorkflowImpl.INPUT_JOB_ID, safeJobId);

        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(TemporalWorkflowType.VIDEO_GENERATION, options);
            WorkflowExecution execution = stub.start(input);
            log.info(
                    "Video generation workflow started: jobId={} workflowId={} runId={} type={} queue={}",
                    safeJobId,
                    execution.getWorkflowId(),
                    execution.getRunId(),
                    TemporalWorkflowType.VIDEO_GENERATION,
                    TemporalTaskQueue.VIDEO_GENERATION);
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
