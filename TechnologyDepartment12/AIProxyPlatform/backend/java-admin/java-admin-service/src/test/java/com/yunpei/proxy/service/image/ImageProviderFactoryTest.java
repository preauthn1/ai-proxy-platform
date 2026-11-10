package com.yunpei.proxy.service.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link ImageProviderFactory} 路由测试。
 */
class ImageProviderFactoryTest {

    @Test
    void provider_default_isPlaceholder() {
        ImageProvider provider = new ImageProviderFactory().provider();
        assertThat(provider).isInstanceOf(PlaceholderImageProvider.class);
    }

    @Test
    void provider_new2api_returnsNew2ApiProvider() {
        ImageProviderProperties props = new ImageProviderProperties();
        props.setProvider("new2api");
        props.getNew2api().setApiKey("test-key");

        ImageProvider provider = new ImageProviderFactory(props).provider();
        assertThat(provider).isInstanceOf(New2ApiImageProvider.class);
    }

    @Test
    void provider_new2api_isCachedSingleton() {
        ImageProviderProperties props = new ImageProviderProperties();
        props.setProvider("new2api");
        props.getNew2api().setApiKey("test-key");

        ImageProviderFactory factory = new ImageProviderFactory(props);
        ImageProvider first = factory.provider();
        ImageProvider second = factory.provider();
        assertThat(first).isSameAs(second);
    }
}
