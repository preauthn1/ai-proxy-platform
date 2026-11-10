package com.yunpei.proxy.service.image;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ImageProvider 工厂：按 {@link ImageProviderProperties#getProvider()} 路由。
 *
 * <p>{@code placeholder}（默认）→ {@link PlaceholderImageProvider}； {@code new2api} →
 * {@link New2ApiImageProvider}。
 *
 * <p>new2api 实例会被缓存，避免重复创建 HttpClient / 解析配置；
 * 进程内并发由 Temporal worker capacity（image-generation task-queue）控制。
 *
 * @author yunpei
 */
@Component
public class ImageProviderFactory {

    private final ImageProviderProperties properties;
    private final Object lock = new Object();
    private volatile ImageProvider cachedNew2api;

    /** 测试便利构造：固定 placeholder。 */
    public ImageProviderFactory() {
        this(new ImageProviderProperties());
    }

    /** Spring 注入主构造。 */
    @Autowired
    public ImageProviderFactory(ImageProviderProperties properties) {
        this.properties = properties == null ? new ImageProviderProperties() : properties;
    }

    /**
     * 返回当前选定的 Provider。
     *
     * @return 图片生成 Provider
     */
    public ImageProvider provider() {
        if (properties.isNew2api()) {
            ImageProvider existing = cachedNew2api;
            if (existing != null) {
                return existing;
            }
            synchronized (lock) {
                if (cachedNew2api == null) {
                    cachedNew2api = new New2ApiImageProvider(properties.getNew2api());
                }
                return cachedNew2api;
            }
        }
        return new PlaceholderImageProvider();
    }
}
