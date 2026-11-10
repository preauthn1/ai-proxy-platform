package com.yunpei.proxy.infra.temporal.workflow.video;

import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

/**
 * 视频生成 Workflow：start 后定时 poll 直至终态。
 *
 * @author yunpei
 */
@WorkflowInterface
public interface VideoGenerationWorkflow {

    /**
     * 执行视频生成（start + 轮询）。
     *
     * @param input 须含非空 {@code jobId}
     * @return 业务结果 Map
     */
    @WorkflowMethod(name = TemporalWorkflowType.VIDEO_GENERATION)
    Map<String, Object> generate(Map<String, Object> input);
}
