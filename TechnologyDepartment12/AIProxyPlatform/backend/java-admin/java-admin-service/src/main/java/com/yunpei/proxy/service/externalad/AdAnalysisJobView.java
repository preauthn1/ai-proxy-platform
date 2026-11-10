package com.yunpei.proxy.service.externalad;

import java.util.Map;

/**
 * GET 轮询视图。
 *
 * @author yunpei
 */
public record AdAnalysisJobView(
        String analysisId,
        String externalRequestId,
        String status,
        String stage,
        int progress,
        String createdAt,
        String startedAt,
        String completedAt,
        Map<String, Object> result,
        AdAnalysisJobError error) {}
