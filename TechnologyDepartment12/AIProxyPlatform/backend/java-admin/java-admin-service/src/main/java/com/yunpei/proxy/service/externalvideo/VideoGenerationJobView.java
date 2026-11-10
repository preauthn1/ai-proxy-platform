package com.yunpei.proxy.service.externalvideo;

/**
 * 视频任务查询视图。
 *
 * @param jobId 任务 id
 * @param status processing | succeeded | failed
 * @param durationSeconds 时长
 * @param aspectRatio 宽高比
 * @param url 成功时视频 URL
 * @param error 失败文案
 * @author yunpei
 */
public record VideoGenerationJobView(
        String jobId, String status, int durationSeconds, String aspectRatio, String url, String error) {}
