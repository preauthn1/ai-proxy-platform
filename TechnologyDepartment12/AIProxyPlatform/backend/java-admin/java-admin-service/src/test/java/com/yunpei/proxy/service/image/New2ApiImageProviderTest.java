package com.yunpei.proxy.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link New2ApiImageProvider} 契约测试（mock HttpClient）。
 */
class New2ApiImageProviderTest {

    @Test
    void constructor_missingApiKey_throws() {
        ImageProviderProperties.New2api cfg = new ImageProviderProperties.New2api();
        assertThatThrownBy(() -> new New2ApiImageProvider(cfg))
                .isInstanceOf(ImageProviderException.class)
                .hasMessageContaining("NEW2API_API_KEY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_parsesUrlAndForcesResponseFormat() throws Exception {
        ImageProviderProperties.New2api cfg = baseCfg();
        cfg.setBaseUrl("http://new2api.example/v1");
        cfg.setModel("image");
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[{\"url\":\"https://cdn.example/img-1.png\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        New2ApiImageProvider provider = new New2ApiImageProvider(cfg, httpClient);
        GeneratedImage image = provider.generate(new ImageBrief(1, "9:16", "a cat", null, null, null));

        assertThat(image.url()).isEqualTo("https://cdn.example/img-1.png");
        assertThat(image.model()).isEqualTo("image");
        assertThat(image.prompt()).isEqualTo("a cat");
        assertThat(image.size()).isEqualTo("9:16");
        assertThat(image.metadata()).containsEntry("provider", "new2api");
        assertThat(image.metadata()).containsEntry("requested_size", "9:16");
        assertThat(image.metadata()).containsEntry("provider_size", "1440x2560");
        assertThat(image.metadata()).containsEntry("response_format", "url");

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any(HttpResponse.BodyHandler.class));
        // body 在 Publisher 内无法直接读；通过二次 mock 请求不校验 body 字符串即可
        assertThat(reqCap.getValue().uri().toString()).isEqualTo("http://new2api.example/v1/images/generations");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_usesBriefModelOverride() throws Exception {
        ImageProviderProperties.New2api cfg = baseCfg();
        cfg.setModel("image");
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[{\"url\":\"https://cdn.example/x.png\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GeneratedImage image = new New2ApiImageProvider(cfg, httpClient)
                .generate(new ImageBrief(1, "1:1", "a dog", "custom-model", null, null));

        assertThat(image.model()).isEqualTo("custom-model");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_httpError_noRetry_throwsOnce() throws Exception {
        ImageProviderProperties.New2api cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":\"unauthorized\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        New2ApiImageProvider provider = new New2ApiImageProvider(cfg, httpClient);
        assertThatThrownBy(() -> provider.generate(new ImageBrief(1, "9:16", "a cat", null, null, null)))
                .isInstanceOf(ImageProviderException.class)
                .hasMessageContaining("HTTP 401");
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_retriesThenSucceeds() throws Exception {
        ImageProviderProperties.New2api cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of(0, 0));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> fail = mock(HttpResponse.class);
        when(fail.statusCode()).thenReturn(429);
        when(fail.body()).thenReturn("{\"error\":\"rate limit\"}");
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("{\"data\":[{\"url\":\"https://cdn.example/ok.png\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(fail)
                .thenReturn(fail)
                .thenReturn(ok);

        GeneratedImage image = new New2ApiImageProvider(cfg, httpClient)
                .generate(new ImageBrief(1, "9:16", "a cat", null, null, null));

        assertThat(image.url()).isEqualTo("https://cdn.example/ok.png");
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_retriesExhausted_throwsLastError() throws Exception {
        ImageProviderProperties.New2api cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of(0));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> fail = mock(HttpResponse.class);
        when(fail.statusCode()).thenReturn(503);
        when(fail.body()).thenReturn("{\"error\":\"busy\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(fail);

        assertThatThrownBy(() -> new New2ApiImageProvider(cfg, httpClient)
                        .generate(new ImageBrief(1, "9:16", "a cat", null, null, null)))
                .isInstanceOf(ImageProviderException.class)
                .hasMessageContaining("HTTP 503");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void toProviderSize_mapsAspectRatioToPixels() {
        assertThat(New2ApiImageProvider.toProviderSize(null)).isNull();
        assertThat(New2ApiImageProvider.toProviderSize("  ")).isNull();
        assertThat(New2ApiImageProvider.toProviderSize("9:16")).isEqualTo("1440x2560");
        assertThat(New2ApiImageProvider.toProviderSize("16:9")).isEqualTo("2560x1440");
        assertThat(New2ApiImageProvider.toProviderSize("1:1")).isEqualTo("2048x2048");
        assertThat(New2ApiImageProvider.toProviderSize("4:5")).isEqualTo("1664x2080");
        assertThat(New2ApiImageProvider.toProviderSize("1024x1024")).isEqualTo("1024x1024");
        assertThat(New2ApiImageProvider.toProviderSize("2K")).isEqualTo("2K");
    }

    @Test
    void normalizeRetryDelays_filtersNullAndNegative() {
        assertThat(New2ApiImageProvider.normalizeRetryDelays(null)).isEmpty();
        assertThat(New2ApiImageProvider.normalizeRetryDelays(List.of())).isEmpty();
        List<Integer> withNulls = new ArrayList<>(Arrays.asList(1, null, -1, 0, 5));
        assertThat(New2ApiImageProvider.normalizeRetryDelays(withNulls)).containsExactly(1, 0, 5);
    }

    private static ImageProviderProperties.New2api baseCfg() {
        ImageProviderProperties.New2api cfg = new ImageProviderProperties.New2api();
        cfg.setApiKey("test-key");
        return cfg;
    }
}
