package com.yunpei.proxy.service.externalad;

/**
 * 分析任务未找到。Controller 映射 HTTP 404 + 外部信封。
 *
 * @author yunpei
 */
public class AdAnalysisNotFoundException extends RuntimeException {

    public AdAnalysisNotFoundException() {
        super("analysis_id was not found.");
    }
}
