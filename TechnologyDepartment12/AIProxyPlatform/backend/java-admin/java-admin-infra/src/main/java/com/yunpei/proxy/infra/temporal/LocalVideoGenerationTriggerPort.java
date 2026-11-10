package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import com.yunpei.proxy.service.port.VideoGenerationTriggerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporal 未启用时的视频触发回退：仅打日志。
 *
 * @author yunpei
 */
public class LocalVideoGenerationTriggerPort implements VideoGenerationTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(LocalVideoGenerationTriggerPort.class);

    @Override
    public TriggerResult start(String jobId) {
        log.warn("Video generation trigger skipped (Temporal disabled): jobId={}", jobId);
        return null;
    }
}
