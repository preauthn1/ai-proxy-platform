package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.temporal.workflow.image.ImageGenerationWorkflowImpl;
import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
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
 * 基于 Temporal {@link WorkflowClient} 的图片生成触发实现。
 *
 * <p>对齐 {@link TemporalTaskTriggerPort}：直接启动业务 Workflow（不再经 JobDispatch 包装），
 * Memo 写入 {@code configCode} 供 {@code ExecutionMirrorTick} 挂接；队列为
 * {@link TemporalTaskQueue#IMAGE_GENERATION}。fire-and-forget：{@code stub.start} 异步启动即返回
 * workflowId/runId，由业务层写入 {@code temporal_task_execution} 种子行。
 *
 * @author yunpei
 */
public class TemporalImageGenerationTriggerPort implements ImageGenerationTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(TemporalImageGenerationTriggerPort.class);

    /** 与 ImageGeneration activity StartToClose 对齐。 */
    static final int TIMEOUT_SECONDS = 120;

    private final WorkflowClient workflowClient;

    public TemporalImageGenerationTriggerPort(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public TriggerResult start(String jobId) {
        String safeJobId = requireJobId(jobId);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId("wf-" + CONFIG_CODE + "-" + safeJobId)
                .setTaskQueue(TemporalTaskQueue.IMAGE_GENERATION)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .setMemo(TemporalTaskTriggerPort.buildMemo(null, CONFIG_CODE))
                .build();

        Map<String, Object> input = Map.of(ImageGenerationWorkflowImpl.INPUT_JOB_ID, safeJobId);

        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(TemporalWorkflowType.IMAGE_GENERATION, options);
            WorkflowExecution execution = stub.start(input);
            log.info(
                    "Image generation workflow started: jobId={} workflowId={} runId={} type={} queue={}",
                    safeJobId,
                    execution.getWorkflowId(),
                    execution.getRunId(),
                    TemporalWorkflowType.IMAGE_GENERATION,
                    TemporalTaskQueue.IMAGE_GENERATION);
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
