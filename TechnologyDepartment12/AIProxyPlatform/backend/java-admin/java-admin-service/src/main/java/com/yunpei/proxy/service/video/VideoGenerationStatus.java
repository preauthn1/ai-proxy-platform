package com.yunpei.proxy.service.video;

import java.util.Map;

/**
 * 上游任务状态。
 *
 * @param providerJobId 上游任务 id
 * @param providerStatus processing | succeeded | failed 等
 * @param videoUrl 成功时视频 URL
 * @param errorMessage 失败文案
 * @param rawResponse 原始响应
 * @author yunpei
 */
public record VideoGenerationStatus(
        String providerJobId,
        String providerStatus,
        String videoUrl,
        String errorMessage,
        Map<String, Object> rawResponse) {}
