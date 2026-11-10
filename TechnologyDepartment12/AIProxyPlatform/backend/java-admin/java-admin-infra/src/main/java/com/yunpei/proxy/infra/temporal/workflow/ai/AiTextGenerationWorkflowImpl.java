package com.yunpei.proxy.infra.temporal.workflow.ai;

import com.yunpei.proxy.service.task.TemporalTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * {@link AiTextGenerationWorkflow} 实现：调用 Activity；LLM 超时后按 RetryOptions 重跑请求。
 *
 * @author yunpei
 */
@WorkflowImpl(taskQueues = AiTextGenerationWorkflowImpl.TASK_QUEUE)
public class AiTextGenerationWorkflowImpl implements AiTextGenerationWorkflow {

    public static final String TASK_QUEUE = TemporalTaskQueue.AI_TEXT_GENERATION;
    public static final String INPUT_JOB_ID = "jobId";

    /** 单次 activity 上限：覆盖 2 分钟 HTTP 硬超时 + 回写余量。 */
    public static final int ACTIVITY_START_TO_CLOSE_SECONDS = 150;

    /** LLM 超时后 Temporal 重跑 activity（重新请求）的最大次数。 */
    public static final int ACTIVITY_MAX_ATTEMPTS = 3;

    private final AiTextGenerationActivity activities = Workflow.newActivityStub(
            AiTextGenerationActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(ACTIVITY_START_TO_CLOSE_SECONDS))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(ACTIVITY_MAX_ATTEMPTS)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofSeconds(10))
                            .build())
                    .build());

    @Override
    public Map<String, Object> generate(Map<String, Object> input) {
        String jobId = requireJobId(input);
        return activities.execute(jobId);
    }

    static String requireJobId(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("ai text generation input is required");
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
