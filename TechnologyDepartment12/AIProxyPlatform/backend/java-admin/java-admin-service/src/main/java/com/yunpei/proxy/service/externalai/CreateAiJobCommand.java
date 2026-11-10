package com.yunpei.proxy.service.externalai;

import java.util.Map;

/**
 * 创建 AI 文本任务的入参命令。
 *
 * @param taskType generation_task.task_type
 * @param externalRequestId 幂等键；可选
 * @param payload 原始请求体字段（落库 payload_json）
 * @author yunpei
 */
public record CreateAiJobCommand(String taskType, String externalRequestId, Map<String, Object> payload) {

    public static CreateAiJobCommand of(String taskType, String externalRequestId, Map<String, Object> payload) {
        return new CreateAiJobCommand(taskType, externalRequestId, payload);
    }
}
