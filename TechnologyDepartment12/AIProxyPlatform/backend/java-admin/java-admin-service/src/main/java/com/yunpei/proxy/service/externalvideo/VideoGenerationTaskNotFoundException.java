package com.yunpei.proxy.service.externalvideo;

/**
 * 视频生成任务未找到（→ HTTP 404 外部信封）。
 *
 * @author yunpei
 */
public class VideoGenerationTaskNotFoundException extends RuntimeException {

    public VideoGenerationTaskNotFoundException(String message) {
        super(message);
    }
}
