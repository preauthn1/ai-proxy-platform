package com.yunpei.proxy.infra.temporal.workflow.ad;

import com.yunpei.proxy.service.task.TemporalTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * {@link AdPerformanceAnalysisWorkflow} 实现：单次调用 Activity 后结束。
 *
 * @author yunpei
 */
@WorkflowImpl(taskQueues = AdPerformanceAnalysisWorkflowImpl.TASK_QUEUE)
public class AdPerformanceAnalysisWorkflowImpl implements AdPerformanceAnalysisWorkflow {

    public static final String TASK_QUEUE = TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS;
    public static final String INPUT_JOB_ID = "jobId";

    private final AdPerformanceAnalysisActivity activities = Workflow.newActivityStub(
            AdPerformanceAnalysisActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(1200))
                    .build());

    @Override
    public Map<String, Object> generate(Map<String, Object> input) {
        String jobId = requireJobId(input);
        return activities.execute(jobId);
    }

    static String requireJobId(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("ad performance analysis input is required");
        }
        Object raw = input.get(INPUT_JOB_ID);
        if (raw == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        String jobId = String.valueOf(raw).trim();
        if (jobId.isEmpty() || "null".equals(jobId)) {
            throw new IllegalArgumentException("jobId is required");
        }
        return jobId;
    }
}
