package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.AiGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 文本生成触发本地回退：Temporal 未启用时不启动 workflow。
 *
 * @author yunpei
 */
public class LocalAiGenerationTriggerPort implements AiGenerationTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(LocalAiGenerationTriggerPort.class);

    @Override
    public TriggerResult start(String jobId) {
        log.warn("AI text generation trigger skipped (Temporal disabled): jobId={}", jobId);
        return null;
    }
}
