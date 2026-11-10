package com.yunpei.proxy.infra.temporal.workflow;

import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 系统镜像 tick：单次对账 Temporal 执行状态 → {@code temporal_task_execution}。
 *
 * <p>由系统 Schedule {@code sys-execution-mirror} 按 interval 驱动，不进入任务配置可选类型。
 *
 * @author yunpei
 */
@WorkflowInterface
public interface ExecutionMirrorTickWorkflow {

    @WorkflowMethod(name = TemporalWorkflowType.EXECUTION_MIRROR_TICK)
    void run();
}
