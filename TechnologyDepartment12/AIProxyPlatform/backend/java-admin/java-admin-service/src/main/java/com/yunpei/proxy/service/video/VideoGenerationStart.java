package com.yunpei.proxy.service.video;

import java.util.Map;

/**
 * 上游创建任务结果。
 *
 * @param providerJobId 上游任务 id
 * @param providerStatus 上游状态
 * @param rawResponse 原始响应（可脱敏）
 * @author yunpei
 */
public record VideoGenerationStart(String providerJobId, String providerStatus, Map<String, Object> rawResponse) {}
