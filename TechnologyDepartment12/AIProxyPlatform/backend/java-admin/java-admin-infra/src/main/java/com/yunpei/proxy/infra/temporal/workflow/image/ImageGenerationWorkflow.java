package com.yunpei.proxy.infra.temporal.workflow.image;

import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

/**
 * 图片生成 Workflow：从入参取出 jobId，内部仅调用 {@link ImageGenerationActivity} 完成生成与回写。
 *
 * <p>由 {@code ImageGenerationTriggerPort} 直接启动（对齐 {@code TemporalTaskTriggerPort}）；
 * 返回业务结果 Map，由 {@code ExecutionMirrorTick} 镜像到 {@code result_summary}。
 * 入参为 Map，须含非空 {@code jobId}。
 *
 * @author yunpei
 */
@WorkflowInterface
public interface ImageGenerationWorkflow {

    /**
     * 执行一次图片生成。
     *
     * @param input 业务入参，须含非空 {@code jobId}（已持久化的图片生成任务 id）
     * @return 业务结果 Map（images/count/size/model_id/jobId 等），供执行记录 result_summary
     */
    @WorkflowMethod(name = TemporalWorkflowType.IMAGE_GENERATION)
    Map<String, Object> generate(Map<String, Object> input);
}
