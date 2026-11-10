package com.yunpei.proxy.service.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link CpaVideoProvider} 契约测试（mock HttpClient）。
 */
class CpaVideoProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void constructor_missingApiKey_throws() {
        VideoProviderProperties.Cpa cfg = new VideoProviderProperties.Cpa();
        assertThatThrownBy(() -> new CpaVideoProvider(cfg))
                .isInstanceOf(VideoProviderException.class)
                .hasMessageContaining("CPA_API_KEY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startGeneration_postsGenerationsWithReferenceImagesOnly() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6\",\"status\":\"queued\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationRequest request = new VideoGenerationRequest(
                "Generate a transition landscape show based on two pictures",
                List.of(
                        new VideoSourceImage("a", "https://example.com/a.png", "first_frame"),
                        new VideoSourceImage("b", "https://example.com/b.png", "last_frame")),
                6,
                "16:9",
                null);

        VideoGenerationStart started = new CpaVideoProvider(cfg, httpClient).startGeneration(request);

        assertThat(started.providerJobId()).isEqualTo("6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6");
        assertThat(started.providerStatus()).isEqualTo("processing");

        HttpRequest sent = captor.getValue();
        assertThat(sent.uri().toString()).isEqualTo("http://cpa.example/v1/videos/generations");
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.headers().firstValue("Authorization")).hasValue("Bearer k");
        assertThat(sent.headers().firstValue("Content-Type")).hasValue("application/json");

        JsonNode node = MAPPER.readTree(readBody(sent));
        assertThat(node.get("model").asText()).isEqualTo("grok-imagine-video");
        assertThat(node.get("prompt").asText()).isEqualTo("Generate a transition landscape show based on two pictures");
        assertThat(node.has("seconds")).isFalse();
        assertThat(node.has("size")).isFalse();
        assertThat(node.has("input_reference")).isFalse();
        assertThat(node.get("reference_images")).hasSize(2);
        assertThat(node.get("reference_images").get(0).get("url").asText()).isEqualTo("https://example.com/a.png");
        assertThat(node.get("reference_images").get(0).has("role")).isFalse();
        assertThat(node.get("reference_images").get(0).has("image_url")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void startGeneration_acceptsTaskIdAlias() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"task_id\":\"task-alias-1\",\"status\":\"processing\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationStart started = new CpaVideoProvider(cfg, httpClient)
                .startGeneration(new VideoGenerationRequest("p", List.of(), 1, null, null));

        assertThat(started.providerJobId()).isEqualTo("task-alias-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startGeneration_acceptsRequestIdOnlyResponse() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"request_id\":\"5e2f7419-eeab-9adc-8ae9-1fba3aa04dbf\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationStart started = new CpaVideoProvider(cfg, httpClient)
                .startGeneration(new VideoGenerationRequest("p", List.of(), 1, null, null));

        assertThat(started.providerJobId()).isEqualTo("5e2f7419-eeab-9adc-8ae9-1fba3aa04dbf");
        assertThat(started.providerStatus()).isEqualTo("processing");
    }

    @Test
    void extractVideoUrl_readsMetadataUrl() throws Exception {
        String body = """
                {
                  "id": "6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6",
                  "status": "completed",
                  "metadata": {
                    "url": "https://cdn.example/out.mp4?sig=1"
                  }
                }
                """;
        assertThat(CpaVideoProvider.extractVideoUrl(MAPPER.readTree(body)))
                .isEqualTo("https://cdn.example/out.mp4?sig=1");
    }

    @Test
    void extractVideoUrl_readsCpaVideoObjectUrl() throws Exception {
        String body = """
                {
                  "status": "done",
                  "video": {
                    "url": "https://vidgen.x.ai/xai-vidgen-bucket/xai-video-620a19f0-9f8c-92bb-a89d-758e1796d57e.mp4",
                    "duration": 8,
                    "respect_moderation": true
                  },
                  "model": "grok-imagine-video",
                  "progress": 100
                }
                """;
        assertThat(CpaVideoProvider.extractVideoUrl(MAPPER.readTree(body)))
                .isEqualTo("https://vidgen.x.ai/xai-vidgen-bucket/xai-video-620a19f0-9f8c-92bb-a89d-758e1796d57e.mp4");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getGenerationStatus_mapsDoneVideoUrl() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                        {
                          "status": "done",
                          "video": {
                            "url": "https://vidgen.x.ai/sample.mp4",
                            "duration": 8
                          },
                          "progress": 100
                        }
                        """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationStatus status = new CpaVideoProvider(cfg, httpClient).getGenerationStatus("req-1");

        assertThat(status.providerStatus()).isEqualTo("succeeded");
        assertThat(status.videoUrl()).isEqualTo("https://vidgen.x.ai/sample.mp4");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getGenerationStatus_mapsCompletedMetadataUrl() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                        {
                          "id": "6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6",
                          "status": "completed",
                          "metadata": {
                            "url": "https://cdn.example/out.mp4?sig=1"
                          }
                        }
                        """);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationStatus status =
                new CpaVideoProvider(cfg, httpClient).getGenerationStatus("6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6");

        assertThat(captor.getValue().uri().toString())
                .isEqualTo("http://cpa.example/v1/videos/6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6");
        assertThat(captor.getValue().method()).isEqualTo("GET");
        assertThat(status.providerStatus()).isEqualTo("succeeded");
        assertThat(status.videoUrl()).isEqualTo("https://cdn.example/out.mp4?sig=1");
        assertThat(status.providerJobId()).isEqualTo("6af8bd0a-1a79-9ef6-9f13-b5dccef90ab6");
    }

    @Test
    void describeThrowable_nullMessage_keepsExceptionType() {
        assertThat(CpaVideoProvider.describeThrowable(new ClosedChannelException()))
                .isEqualTo("ClosedChannelException");
        assertThat(CpaVideoProvider.describeThrowable(new IOException((String) null)))
                .isEqualTo("IOException");
        assertThat(CpaVideoProvider.describeThrowable(new IOException("top", new ClosedChannelException())))
                .isEqualTo("IOException: top caused by ClosedChannelException");
    }

    @Test
    void connectivityHint_forConnectException_includesHostAndProviderAdvice() throws Exception {
        java.net.URI uri = java.net.URI.create("http://154.89.195.173:8318/v1/videos/generations");
        java.net.ConnectException ex = new java.net.ConnectException();
        assertThat(CpaVideoProvider.isConnectivityFailure(ex)).isTrue();
        assertThat(CpaVideoProvider.connectivityHint(ex, uri))
                .contains("154.89.195.173:8318")
                .contains("VIDEO_PROVIDER=new2api");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exchange_ioExceptionWithNullMessage_includesTypeAndUri() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ClosedChannelException());

        assertThatThrownBy(() -> new CpaVideoProvider(cfg, httpClient)
                        .startGeneration(new VideoGenerationRequest("p", List.of(), 1, null, null)))
                .isInstanceOf(VideoProviderException.class)
                .hasMessageContaining("ClosedChannelException")
                .hasMessageContaining("POST")
                .hasMessageContaining("/videos/generations")
                .hasMessageContaining("cannot reach")
                .hasMessageNotContaining("failed: null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exchange_http4xx_doesNotRetry() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of(0, 0));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"error\":\"bad request\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> new CpaVideoProvider(cfg, httpClient)
                        .startGeneration(new VideoGenerationRequest("p", List.of(), 1, null, null)))
                .isInstanceOf(VideoProviderException.class)
                .hasMessageContaining("HTTP 400")
                .extracting(ex -> ((VideoProviderException) ex).httpStatus())
                .isEqualTo(400);

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exchange_http5xx_retriesThenSucceeds() throws Exception {
        VideoProviderProperties.Cpa cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of(0));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> fail = mock(HttpResponse.class);
        when(fail.statusCode()).thenReturn(502);
        when(fail.body()).thenReturn("bad gateway");
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("{\"id\":\"job-1\",\"status\":\"queued\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(fail, ok);

        VideoGenerationStart started = new CpaVideoProvider(cfg, httpClient)
                .startGeneration(new VideoGenerationRequest("p", List.of(), 1, null, null));

        assertThat(started.providerJobId()).isEqualTo("job-1");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static VideoProviderProperties.Cpa baseCfg() {
        VideoProviderProperties.Cpa cfg = new VideoProviderProperties.Cpa();
        cfg.setApiKey("k");
        cfg.setBaseUrl("http://cpa.example/v1");
        cfg.setModel("grok-imagine-video");
        return cfg;
    }

    private static String readBody(HttpRequest request) throws InterruptedException {
        HttpRequest.BodyPublisher publisher =
                request.bodyPublisher().orElseThrow(() -> new IllegalStateException("no body"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        if (!done.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timeout reading request body");
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
