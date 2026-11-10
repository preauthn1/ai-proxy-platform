package com.yunpei.proxy.infra.temporal.workflow.ai;

import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

/**
 * AI 文本生成 Workflow：入参 jobId，Activity 委托服务 execute（按 task_type 分发）。
 *
 * @author yunpei
 */
@WorkflowInterface
public interface AiTextGenerationWorkflow {

    /**
     * 执行一次 AI 文本生成。
     *
     * @param input 须含非空 {@code jobId}
     * @return 业务结果 Map
     */
    @WorkflowMethod(name = TemporalWorkflowType.AI_TEXT_GENERATION)
    Map<String, Object> generate(Map<String, Object> input);
}
