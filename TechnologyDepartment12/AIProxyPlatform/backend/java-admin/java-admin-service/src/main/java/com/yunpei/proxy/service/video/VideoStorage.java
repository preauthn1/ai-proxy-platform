package com.yunpei.proxy.service.video;

import com.yunpei.proxy.service.image.TransferResult;

/**
 * 视频结果传输抽象：将上游 URL 落到对象存储（或直通）。
 *
 * @author yunpei
 */
public interface VideoStorage {

    /**
     * 传输已生成视频。
     *
     * @param sourceUrl 上游视频 URL
     * @param jobId 任务 id
     * @return 公开 URL 与 storage key
     */
    TransferResult transfer(String sourceUrl, String jobId);
}
