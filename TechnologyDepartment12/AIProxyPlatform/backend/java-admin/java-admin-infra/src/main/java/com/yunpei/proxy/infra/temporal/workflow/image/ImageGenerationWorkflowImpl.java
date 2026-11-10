package com.yunpei.proxy.infra.temporal.workflow.image;

import com.yunpei.proxy.service.task.TemporalTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * {@link ImageGenerationWorkflow} 实现：单次调用 {@link ImageGenerationActivity} 后结束。
 *
 * <p>多图在 activity 内串行；进程内并发由 worker capacity（{@link TemporalTaskQueue#IMAGE_GENERATION}）控制。
 * 入参 Map 字段 {@code jobId} 由 {@code ImageGenerationTriggerPort} 写入。
 *
 * @author yunpei
 */
@WorkflowImpl(taskQueues = ImageGenerationWorkflowImpl.TASK_QUEUE)
public class ImageGenerationWorkflowImpl implements ImageGenerationWorkflow {

    /** 图片生成专属任务队列，activity 与其保持一致。 */
    public static final String TASK_QUEUE = TemporalTaskQueue.IMAGE_GENERATION;

    /** Trigger 传入的任务 id 字段名。 */
    public static final String INPUT_JOB_ID = "jobId";

    private final ImageGenerationActivity activities = Workflow.newActivityStub(
            ImageGenerationActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(1200))
                    .build());

    @Override
    public Map<String, Object> generate(Map<String, Object> input) {
        String jobId = requireJobId(input);
        // Activity 返回业务 result Map → Workflow 结果 → 镜像 result_summary（非标量，不包 value）
        return activities.execute(jobId);
    }

    /** 从派发入参取出非空 jobId。 */
    static String requireJobId(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("image generation input is required");
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
