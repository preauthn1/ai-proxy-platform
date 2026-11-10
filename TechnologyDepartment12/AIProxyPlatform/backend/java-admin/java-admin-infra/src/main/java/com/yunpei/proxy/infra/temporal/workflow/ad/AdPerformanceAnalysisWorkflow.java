package com.yunpei.proxy.infra.temporal.workflow.ad;

import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

/**
 * 广告效果分析 Workflow：入参 jobId，Activity 委托服务 execute。
 *
 * @author yunpei
 */
@WorkflowInterface
public interface AdPerformanceAnalysisWorkflow {

    /**
     * 执行一次广告效果分析。
     *
     * @param input 须含非空 {@code jobId}
     * @return 业务结果 Map
     */
    @WorkflowMethod(name = TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS)
    Map<String, Object> generate(Map<String, Object> input);
}
