package com.yunpei.proxy.infra.temporal.workflow;

import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

/**
 * 测试用 Workflow：单次将进程内计数器 +1 并写日志。
 *
 * <p>对齐 seed {@code log_count_tick} / {@link TemporalWorkflowType#LOG_COUNT_TICK}。
 * 周期执行由 Temporal Schedule（cron / interval）负责，本 Workflow 不做 sleep 循环。
 *
 * <p>入参/出参均为 Map：触发时写入的 input 会进执行记录 input_summary；
 * 完成后 getResult 的 Map 会进 result_summary。
 *
 * @author yunpei
 */
@WorkflowInterface
public interface LogCountTickWorkflow {

    /**
     * 执行一次 tick。
     *
     * @param input 业务入参（可空；schedule 触发会带 trigger/configCode）
     * @return 业务结果摘要（Map），镜像后作为 result_summary
     */
    @WorkflowMethod(name = TemporalWorkflowType.LOG_COUNT_TICK)
    Map<String, Object> run(Map<String, Object> input);
}
