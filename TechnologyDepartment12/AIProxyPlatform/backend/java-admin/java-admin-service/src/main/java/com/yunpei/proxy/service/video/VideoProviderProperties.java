package com.yunpei.proxy.service.video;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视频生成 Provider 配置（{@code video.*}）。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "video")
public class VideoProviderProperties {

    /** placeholder | new2api | cpa */
    private String provider = "placeholder";

    private New2api new2api = new New2api();

    private Cpa cpa = new Cpa();

    /** Workflow 轮询间隔秒。 */
    private int pollIntervalSeconds = 5;

    /** Workflow 执行总超时秒。 */
    private int workflowTimeoutSeconds = 1800;

    public boolean isNew2api() {
        return "new2api".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    public boolean isCpa() {
        return "cpa".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    @Data
    public static class New2api {
        private String apiKey;
        private String baseUrl = "http://154.89.195.173:3001/v1";
        private String model = "video";
        private int timeoutSeconds = 120;
        private List<Integer> retryDelaysSeconds = new ArrayList<>(List.of(1, 2, 5));
    }

    @Data
    public static class Cpa {
        private String apiKey;
        private String baseUrl = "http://154.89.195.173:8318/v1";
        private String model = "grok-imagine-video";
        private int timeoutSeconds = 120;
        private List<Integer> retryDelaysSeconds = new ArrayList<>(List.of(1, 2, 5));
    }
}
