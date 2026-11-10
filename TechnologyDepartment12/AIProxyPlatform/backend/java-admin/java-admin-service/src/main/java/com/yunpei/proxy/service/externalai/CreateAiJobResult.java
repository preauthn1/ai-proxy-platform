package com.yunpei.proxy.service.externalai;

/**
 * 建任务结果（供 controller 组装创建信封）。
 *
 * @param jobId 任务 id（与 asy_task_id 同值）
 * @param status 创建时恒为 processing
 * @author yunpei
 */
public record CreateAiJobResult(String jobId, String status) {}
