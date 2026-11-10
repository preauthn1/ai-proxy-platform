package com.yunpei.proxy.service.port;

/**
 * 视频生成任务触发端口。
 *
 * @author yunpei
 */
public interface VideoGenerationTriggerPort {

    String CONFIG_CODE = "video_generation";

    /**
     * 触发视频生成 Workflow。
     *
     * @param jobId 已落库 processing 任务 id
     * @return Temporal 启动结果；未启用时 null
     */
    TaskTriggerPort.TriggerResult start(String jobId);
}
