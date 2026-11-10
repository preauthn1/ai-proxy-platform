package com.yunpei.proxy.service.port;

/**
 * AI 文本生成任务触发端口：将已落库的任务提交给执行后端（Temporal / 本地回退）。
 *
 * @author yunpei
 */
public interface AiGenerationTriggerPort {

    /**
     * Memo / 执行镜像挂接用 config code（与 workflowId 前缀 {@code wf-ai_text_generation-...} 对齐）。
     */
    String CONFIG_CODE = "ai_text_generation";

    /**
     * 触发 AI 文本生成执行一次。
     *
     * @param jobId 已持久化的 processing 任务 id
     * @return Temporal 启动结果；未启用时 null（不写执行记录）
     */
    TaskTriggerPort.TriggerResult start(String jobId);
}
