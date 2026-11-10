package com.yunpei.proxy.service.externalvideo;

/**
 * 创建视频任务结果。
 *
 * @param jobId 任务 id
 * @param status 状态
 * @param durationSeconds 时长
 * @param aspectRatio 宽高比
 * @author yunpei
 */
public record CreateVideoJobResult(String jobId, String status, int durationSeconds, String aspectRatio) {}
