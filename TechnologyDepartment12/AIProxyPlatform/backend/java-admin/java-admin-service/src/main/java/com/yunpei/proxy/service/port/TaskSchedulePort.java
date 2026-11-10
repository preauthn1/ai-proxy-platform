package com.yunpei.proxy.service.port;

import com.yunpei.proxy.service.entity.TemporalTaskConfig;

/**
 * 任务调度端口：将 DB 任务配置同步到 Temporal Schedule。
 *
 * <p>业务层只依赖本接口（infra {@code TemporalTaskScheduleSync} 实现）。CRUD 写库后应调用
 * {@link #apply}；启动全量对账由 syncAll 承担。Temporal 为必要依赖，无 no-op 回退。
 *
 * @author yunpei
 */
public interface TaskSchedulePort {

    /**
     * 按单条配置应用调度状态。
     *
     * <p>规则与启动同步一致：启用且 cron 非空 → upsert；否则 pause（若已存在）。
     *
     * @param config 最新配置快照（含 code / workflowType / taskQueue / cron / isEnabled 等）
     */
    void apply(TemporalTaskConfig config);
}
