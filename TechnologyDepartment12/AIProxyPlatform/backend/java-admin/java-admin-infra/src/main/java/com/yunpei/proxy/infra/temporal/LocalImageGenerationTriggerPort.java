package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.port.ImageGenerationTriggerPort;
import com.yunpei.proxy.service.port.TaskTriggerPort.TriggerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图片生成触发本地回退实现：Temporal 未启用时不启动 workflow，仅打告警日志。
 *
 * <p>用于保证未配置 Temporal 连接时 {@code createJob} 仍可落库成功；此时任务保持
 * {@code processing}，待启用 Temporal 后可重新触发。返回 {@code null}，调用方不写执行记录。
 *
 * @author yunpei
 */
public class LocalImageGenerationTriggerPort implements ImageGenerationTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(LocalImageGenerationTriggerPort.class);

    @Override
    public TriggerResult start(String jobId) {
        log.warn("Image generation trigger skipped (Temporal disabled): jobId={}", jobId);
        return null;
    }
}
