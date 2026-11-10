package com.yunpei.proxy.service.externalad;

/**
 * 分析任务已落库但派发失败；调用方应使用同一 {@code external_request_id} 重试。
 *
 * @author yunpei
 */
public class AdAnalysisDispatchException extends RuntimeException {

    private final String analysisId;
    private final String externalRequestId;

    public AdAnalysisDispatchException(String analysisId, String externalRequestId, Throwable cause) {
        super("analysis task dispatch failed; retry the same external_request_id", cause);
        this.analysisId = analysisId;
        this.externalRequestId = externalRequestId;
    }

    public String analysisId() {
        return analysisId;
    }

    public String externalRequestId() {
        return externalRequestId;
    }
}
