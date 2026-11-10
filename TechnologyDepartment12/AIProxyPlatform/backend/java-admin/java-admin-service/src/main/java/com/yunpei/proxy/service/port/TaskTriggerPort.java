package com.yunpei.proxy.service.port;

import java.util.Map;

/**
 * 任务触发端口：将「配置 + 入参」提交给 Temporal（由 infra 的 TemporalTaskTriggerPort 实现）。
 *
 * <p>业务层只依赖本接口，不直接耦合 Temporal Client，便于单测 mock。
 *
 * @author yunpei
 */
public interface TaskTriggerPort {

    /**
     * 启动一次任务执行。
     *
     * @param request 启动请求（配置快照 + 入参）
     * @return workflowId / runId 等启动结果
     */
    TriggerResult start(TriggerRequest request);

    /**
     * 启动请求：配置快照 + 业务入参。
     *
     * @param configId       配置 id
     * @param code           任务编码
     * @param workflowType   workflow 类型
     * @param taskQueue      队列
     * @param cronExpr       cron（可空）
     * @param retryPolicy    重试策略 JSON 对象（可空）
     * @param timeoutSeconds 超时秒（可空）
     * @param input          业务入参（可空）
     */
    record TriggerRequest(
            Long configId,
            String code,
            String workflowType,
            String taskQueue,
            String cronExpr,
            Map<String, Object> retryPolicy,
            Integer timeoutSeconds,
            Map<String, Object> input) {}

    /**
     * 启动结果：workflow / run 标识。
     *
     * @param workflowId Temporal / 本地生成的 workflow id
     * @param runId      Temporal / 本地生成的 run id
     */
    record TriggerResult(String workflowId, String runId) {}
}
