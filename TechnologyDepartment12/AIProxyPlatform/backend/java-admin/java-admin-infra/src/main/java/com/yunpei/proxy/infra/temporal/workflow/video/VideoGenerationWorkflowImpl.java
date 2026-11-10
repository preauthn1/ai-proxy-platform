package com.yunpei.proxy.infra.temporal.workflow.video;

import com.yunpei.proxy.service.task.TemporalTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * {@link VideoGenerationWorkflow}：start 后按固定间隔 poll，直到 succeeded/failed。
 *
 * @author yunpei
 */
@WorkflowImpl(taskQueues = VideoGenerationWorkflowImpl.TASK_QUEUE)
public class VideoGenerationWorkflowImpl implements VideoGenerationWorkflow {

    public static final String TASK_QUEUE = TemporalTaskQueue.VIDEO_GENERATION;
    public static final String INPUT_JOB_ID = "jobId";

    /** 默认轮询间隔（秒）；可被 activity 侧配置间接影响，Workflow 内用固定间隔。 */
    static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;

    /** 最大轮询次数：约 1800s / 5s = 360（与默认 workflow 超时对齐）。 */
    static final int MAX_POLL_ATTEMPTS = 360;

    private final VideoGenerationActivity activities = Workflow.newActivityStub(
            VideoGenerationActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(120))
                    .build());

    @Override
    public Map<String, Object> generate(Map<String, Object> input) {
        String jobId = requireJobId(input);
        Map<String, Object> started = activities.start(jobId);
        if (isTerminal(started)) {
            return started;
        }
        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            Workflow.sleep(Duration.ofSeconds(DEFAULT_POLL_INTERVAL_SECONDS));
            Map<String, Object> polled = activities.poll(jobId);
            if (isTerminal(polled)) {
                return polled;
            }
        }
        // 超时仍 processing：最后一次 poll 结果返回（业务层可另有超时失败策略）
        return activities.poll(jobId);
    }

    private static boolean isTerminal(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        Object status = result.get("status");
        if (status == null) {
            // succeeded 结果可能没有 status，但有 url
            return result.containsKey("url");
        }
        String s = String.valueOf(status);
        return "succeeded".equals(s) || "failed".equals(s);
    }

    static String requireJobId(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("video generation input is required");
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
