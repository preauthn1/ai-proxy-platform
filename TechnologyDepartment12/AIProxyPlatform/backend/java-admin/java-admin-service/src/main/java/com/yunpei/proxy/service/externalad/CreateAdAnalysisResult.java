package com.yunpei.proxy.service.externalad;

/**
 * 创建分析任务结果。
 *
 * @param analysisId 对外 analysis_id
 * @param externalRequestId 幂等键
 * @param status queued / processing / succeeded / failed
 * @param stage 当前阶段
 * @param createdAt ISO-8601
 * @param pollUrl 轮询地址
 * @param idempotentReplay 是否为同内容回放
 * @author yunpei
 */
public record CreateAdAnalysisResult(
        String analysisId,
        String externalRequestId,
        String status,
        String stage,
        String createdAt,
        String pollUrl,
        boolean idempotentReplay) {}
