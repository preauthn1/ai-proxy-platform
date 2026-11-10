package com.yunpei.proxy.service.video;

import java.util.List;
import java.util.Map;

/**
 * 上游视频生成请求。
 *
 * @param prompt 分镜/提示词
 * @param sourceImages 源图（0 或 2）
 * @param durationSeconds 时长
 * @param aspectRatio 宽高比
 * @param metadata 附加元数据
 * @author yunpei
 */
public record VideoGenerationRequest(
        String prompt,
        List<VideoSourceImage> sourceImages,
        int durationSeconds,
        String aspectRatio,
        Map<String, Object> metadata) {}
