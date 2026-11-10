package com.yunpei.proxy.service.video;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 占位视频 Provider：start 返回 queued；首次 getStatus 即 succeeded 并给出假 URL。
 *
 * @author yunpei
 */
public class PlaceholderVideoProvider implements VideoProvider {

    private static final String BASE = "https://video.example/generated/";

    @Override
    public VideoGenerationStart startGeneration(VideoGenerationRequest request) {
        String id = "placeholder-video-" + UUID.randomUUID();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", id);
        raw.put("status", "queued");
        raw.put("provider", "placeholder");
        return new VideoGenerationStart(id, "queued", raw);
    }

    @Override
    public VideoGenerationStatus getGenerationStatus(String providerJobId) {
        String url = BASE + providerJobId + ".mp4";
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", providerJobId);
        raw.put("status", "succeeded");
        raw.put("provider", "placeholder");
        raw.put("video_url", url);
        return new VideoGenerationStatus(providerJobId, "succeeded", url, null, raw);
    }
}
