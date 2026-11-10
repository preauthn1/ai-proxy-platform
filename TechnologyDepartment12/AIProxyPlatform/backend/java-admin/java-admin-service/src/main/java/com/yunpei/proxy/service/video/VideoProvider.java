package com.yunpei.proxy.service.video;

/**
 * 视频生成上游抽象。
 *
 * @author yunpei
 */
public interface VideoProvider {

    /** 创建异步视频任务。 */
    VideoGenerationStart startGeneration(VideoGenerationRequest request);

    /** 查询异步任务状态。 */
    VideoGenerationStatus getGenerationStatus(String providerJobId);
}
