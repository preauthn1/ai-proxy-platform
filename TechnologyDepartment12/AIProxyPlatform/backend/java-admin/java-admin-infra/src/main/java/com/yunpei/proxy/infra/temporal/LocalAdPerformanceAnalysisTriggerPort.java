package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.AdPerformanceAnalysisTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 广告分析触发本地回退：Temporal 未启用时不启动 workflow。
 *
 * @author yunpei
 */
public class LocalAdPerformanceAnalysisTriggerPort implements AdPerformanceAnalysisTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(LocalAdPerformanceAnalysisTriggerPort.class);

    @Override
    public TriggerResult start(String analysisId) {
        log.warn("Ad performance analysis trigger skipped (Temporal disabled): analysisId={}", analysisId);
        return null;
    }
}
