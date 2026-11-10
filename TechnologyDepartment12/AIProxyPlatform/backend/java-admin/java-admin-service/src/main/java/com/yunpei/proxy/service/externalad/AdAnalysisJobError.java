package com.yunpei.proxy.service.externalad;

/**
 * 失败任务错误信息。
 *
 * @author yunpei
 */
public record AdAnalysisJobError(String errorCode, String message, boolean retryable) {}
