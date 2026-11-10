package com.yunpei.proxy.service.externalad;

/**
 * 同一 {@code external_request_id} 复用了不同规范化 payload。
 *
 * @author yunpei
 */
public class AdAnalysisIdempotencyConflict extends RuntimeException {

    public AdAnalysisIdempotencyConflict() {
        super("external_request_id already exists with a different normalized payload.");
    }
}
