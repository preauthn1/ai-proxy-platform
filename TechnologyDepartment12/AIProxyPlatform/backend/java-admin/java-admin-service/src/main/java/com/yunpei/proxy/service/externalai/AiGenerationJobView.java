package com.yunpei.proxy.service.externalai;

import java.util.Map;

/**
 * AI 任务详情视图（GET 用）。
 *
 * @param jobId 任务 id
 * @param status succeeded | failed | processing
 * @param result 成功时 result_json 解析后的 Map；非成功为空 Map
 * @param error 失败时错误文案
 * @author yunpei
 */
public record AiGenerationJobView(String jobId, String status, Map<String, Object> result, String error) {}
