package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.temporal.workflow.ad.AdPerformanceAnalysisWorkflowImpl;
import com.yunpei.proxy.service.port.AdPerformanceAnalysisTriggerPort;
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
 * Temporal 广告效果分析触发：启动 {@code AdPerformanceAnalysisWorkflow}。
 *
 * @author yunpei
 */
public class TemporalAdPerformanceAnalysisTriggerPort implements AdPerformanceAnalysisTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(TemporalAdPerformanceAnalysisTriggerPort.class);

    static final int TIMEOUT_SECONDS = 300;

    private final WorkflowClient workflowClient;

    public TemporalAdPerformanceAnalysisTriggerPort(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public TriggerResult start(String analysisId) {
        String safeId = requireAnalysisId(analysisId);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId("wf-" + CONFIG_CODE + "-" + safeId)
                .setTaskQueue(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .setMemo(TemporalTaskTriggerPort.buildMemo(null, CONFIG_CODE))
                .build();

        Map<String, Object> input = Map.of(AdPerformanceAnalysisWorkflowImpl.INPUT_JOB_ID, safeId);

        try {
            WorkflowStub stub =
                    workflowClient.newUntypedWorkflowStub(TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS, options);
            WorkflowExecution execution = stub.start(input);
            log.info(
                    "Ad performance analysis workflow started: analysisId={} workflowId={} runId={} type={} queue={}",
                    safeId,
                    execution.getWorkflowId(),
                    execution.getRunId(),
                    TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS,
                    TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS);
            return new TriggerResult(execution.getWorkflowId(), execution.getRunId());
        } catch (WorkflowServiceException ex) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "Temporal start failed: " + ex.getMessage());
        }
    }

    private static String requireAnalysisId(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "jobId is required");
        }
        return analysisId.trim();
    }
}
