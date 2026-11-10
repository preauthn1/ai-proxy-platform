package com.yunpei.proxy.service.port;

/**
 * 广告效果分析任务触发端口：将已落库的 queued 任务提交给执行后端。
 *
 * @author yunpei
 */
public interface AdPerformanceAnalysisTriggerPort {

    /** Memo / 执行镜像挂接用 config code。 */
    String CONFIG_CODE = "ad_performance_analysis";

    /**
     * 触发一次分析执行。
     *
     * @param analysisId 已持久化的 analysis_id
     * @return Temporal 启动结果；未启用时 null
     */
    TaskTriggerPort.TriggerResult start(String analysisId);
}
