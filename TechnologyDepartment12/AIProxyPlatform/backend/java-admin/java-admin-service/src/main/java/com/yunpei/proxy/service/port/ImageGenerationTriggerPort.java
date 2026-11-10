package com.yunpei.proxy.service.port;

/**
 * 图片生成任务触发端口：将已落库的任务提交给执行后端（Temporal / 本地回退）。
 *
 * <p>业务层只依赖本接口，不直接耦合 Temporal {@code WorkflowClient}，便于单测与替换实现；
 * 对应 infra 的 {@code TemporalImageGenerationTriggerPort} / {@code LocalImageGenerationTriggerPort}。
 *
 * @author yunpei
 */
public interface ImageGenerationTriggerPort {

    /**
     * Memo / 执行镜像挂接用 config code（与 workflowId 前缀 {@code wf-image_generation-...} 对齐）。
     *
     * <p>可不存在对应 {@code temporal_task_config} 行；此时执行记录 {@code config_id} 可空。
     */
    String CONFIG_CODE = "image_generation";

    /**
     * 触发图片生成执行一次。
     *
     * <p>入参为已持久化的 {@code processing} 任务 id。Temporal 实现直接启动
     * {@code ImageGenerationWorkflow}（队列 {@code image-generation}），Memo 挂
     * {@code configCode} 供 ExecutionMirrorTick 镜像；workflow 内 activity 完成生成与回写。
     *
     * <p>调用方应在返回非 null 时立即插入 {@code temporal_task_execution} 种子行，供镜像 tick
     * 双轨① describe 推进终态。
     *
     * @param jobId 图片生成任务 id
     * @return Temporal 启动结果（workflowId/runId）；Temporal 未启用时 null（不写执行记录）
     */
    TaskTriggerPort.TriggerResult start(String jobId);
}
