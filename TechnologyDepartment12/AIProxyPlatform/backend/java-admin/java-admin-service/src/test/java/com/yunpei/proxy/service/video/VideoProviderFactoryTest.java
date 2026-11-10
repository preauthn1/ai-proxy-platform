package com.yunpei.proxy.service.video;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link VideoProviderFactory} 路由测试。
 */
class VideoProviderFactoryTest {

    @Test
    void provider_default_isPlaceholder() {
        VideoProviderFactory factory = new VideoProviderFactory();
        assertThat(factory.provider()).isInstanceOf(PlaceholderVideoProvider.class);
    }

    @Test
    void provider_new2api_returnsNew2ApiProvider() {
        VideoProviderProperties props = new VideoProviderProperties();
        props.setProvider("new2api");
        props.getNew2api().setApiKey("k");
        props.getNew2api().setBaseUrl("http://example/v1");
        props.getNew2api().setModel("video");
        VideoProviderFactory factory = new VideoProviderFactory(props);
        assertThat(factory.provider()).isInstanceOf(New2ApiVideoProvider.class);
    }

    @Test
    void provider_cpa_returnsCpaProvider() {
        VideoProviderProperties props = new VideoProviderProperties();
        props.setProvider("cpa");
        props.getCpa().setApiKey("k");
        props.getCpa().setBaseUrl("http://cpa.example/v1");
        props.getCpa().setModel("grok-imagine-video");
        VideoProviderFactory factory = new VideoProviderFactory(props);
        assertThat(factory.provider()).isInstanceOf(CpaVideoProvider.class);
    }
}
