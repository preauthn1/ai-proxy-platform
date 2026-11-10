package com.yunpei.proxy.service.video;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * VideoProvider 工厂：placeholder（默认）、new2api 或 cpa。
 *
 * @author yunpei
 */
@Component
public class VideoProviderFactory {

    private final VideoProviderProperties properties;
    private final Object lock = new Object();
    private volatile VideoProvider cachedNew2api;
    private volatile VideoProvider cachedCpa;

    public VideoProviderFactory() {
        this(new VideoProviderProperties());
    }

    @Autowired
    public VideoProviderFactory(VideoProviderProperties properties) {
        this.properties = properties == null ? new VideoProviderProperties() : properties;
    }

    public VideoProvider provider() {
        if (properties.isCpa()) {
            VideoProvider existing = cachedCpa;
            if (existing != null) {
                return existing;
            }
            synchronized (lock) {
                if (cachedCpa == null) {
                    cachedCpa = new CpaVideoProvider(properties.getCpa());
                }
                return cachedCpa;
            }
        }
        if (properties.isNew2api()) {
            VideoProvider existing = cachedNew2api;
            if (existing != null) {
                return existing;
            }
            synchronized (lock) {
                if (cachedNew2api == null) {
                    cachedNew2api = new New2ApiVideoProvider(properties.getNew2api());
                }
                return cachedNew2api;
            }
        }
        return new PlaceholderVideoProvider();
    }
}
