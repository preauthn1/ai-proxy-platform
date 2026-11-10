package com.yunpei.proxy.service.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link New2ApiVideoProvider} 契约测试（mock HttpClient + 真实查询响应形态）。
 */
class New2ApiVideoProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void constructor_missingApiKey_throws() {
        VideoProviderProperties.New2api cfg = new VideoProviderProperties.New2api();
        assertThatThrownBy(() -> new New2ApiVideoProvider(cfg))
                .isInstanceOf(VideoProviderException.class)
                .hasMessageContaining("NEW2API_API_KEY");
    }

    @Test
    void extractVideoUrl_readsMetadataUrlFromCompletedSeedanceShape() throws Exception {
        String body = """
                {
                  "id": "task_ppk8XzlSFQXxb0jYwGSXWAl0p2v7ifhY",
                  "task_id": "task_ppk8XzlSFQXxb0jYwGSXWAl0p2v7ifhY",
                  "object": "video",
                  "model": "video",
                  "status": "completed",
                  "progress": 100,
                  "created_at": 1786346637,
                  "completed_at": 1786346810,
                  "metadata": {
                    "url": "https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/doubao-seedance-2-0-mini/sample.mp4?X-Tos-Algorithm=TOS4-HMAC-SHA256"
                  }
                }
                """;
        String url = New2ApiVideoProvider.extractVideoUrl(MAPPER.readTree(body));
        assertThat(url)
                .startsWith("https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/")
                .contains("sample.mp4");
    }

    @Test
    void extractVideoUrl_prefersTopLevelUrl() throws Exception {
        String body =
                "{\"status\":\"completed\",\"url\":\"https://cdn.example/a.mp4\",\"metadata\":{\"url\":\"https://cdn.example/b.mp4\"}}";
        assertThat(New2ApiVideoProvider.extractVideoUrl(MAPPER.readTree(body))).isEqualTo("https://cdn.example/a.mp4");
    }

    @Test
    void extractVideoUrl_readsXaiTaskEnvelopeResultUrl() throws Exception {
        String body = """
                {
                  "code": "success",
                  "message": "",
                  "data": {
                    "id": 29,
                    "task_id": "task_GNasyg3jTfqYOUDr8crJDADNqEu5PbZI",
                    "platform": "48",
                    "status": "SUCCESS",
                    "fail_reason": "",
                    "result_url": "https://vidgen.x.ai/xai-vidgen-bucket/xai-video-c5e2b7b3-4ac3-9cb4-abd8-b6b70d761cb2.mp4",
                    "progress": "100%",
                    "data": {
                      "model": "grok-imagine-video",
                      "progress": 100,
                      "status": "done",
                      "video": {
                        "duration": 10,
                        "respect_moderation": true,
                        "url": "https://vidgen.x.ai/xai-vidgen-bucket/xai-video-c5e2b7b3-4ac3-9cb4-abd8-b6b70d761cb2.mp4"
                      }
                    }
                  }
                }
                """;
        assertThat(New2ApiVideoProvider.extractVideoUrl(MAPPER.readTree(body)))
                .isEqualTo("https://vidgen.x.ai/xai-vidgen-bucket/xai-video-c5e2b7b3-4ac3-9cb4-abd8-b6b70d761cb2.mp4");
    }

    @Test
    void extractVideoUrl_readsNestedVideoUrlWhenResultUrlMissing() throws Exception {
        String body = """
                {
                  "code": "success",
                  "data": {
                    "status": "SUCCESS",
                    "data": {
                      "status": "done",
                      "video": {
                        "url": "https://vidgen.x.ai/xai-vidgen-bucket/nested-only.mp4"
                      }
                    }
                  }
                }
                """;
        assertThat(New2ApiVideoProvider.extractVideoUrl(MAPPER.readTree(body)))
                .isEqualTo("https://vidgen.x.ai/xai-vidgen-bucket/nested-only.mp4");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getGenerationStatus_mapsCompletedMetadataUrl() throws Exception {
        VideoProviderProperties.New2api cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        String body = """
                {
                  "id": "task_ppk8XzlSFQXxb0jYwGSXWAl0p2v7ifhY",
                  "status": "completed",
                  "progress": 100,
                  "metadata": {
                    "url": "https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/doubao-seedance-2-0-mini/x.mp4?sig=1"
                  }
                }
                """;
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationStatus status =
                new New2ApiVideoProvider(cfg, httpClient).getGenerationStatus("task_ppk8XzlSFQXxb0jYwGSXWAl0p2v7ifhY");

        assertThat(status.providerStatus()).isEqualTo("succeeded");
        assertThat(status.videoUrl())
                .isEqualTo("https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/doubao-seedance-2-0-mini/x.mp4?sig=1");
        assertThat(status.providerJobId()).isEqualTo("task_ppk8XzlSFQXxb0jYwGSXWAl0p2v7ifhY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getGenerationStatus_mapsXaiTaskEnvelope() throws Exception {
        VideoProviderProperties.New2api cfg = baseCfg();
        cfg.setRetryDelaysSeconds(List.of());

        String body = """
                {
                  "code": "success",
                  "message": "",
                  "data": {
                    "id": 29,
                    "task_id": "task_GNasyg3jTfqYOUDr8crJDADNqEu5PbZI",
                    "status": "SUCCESS",
                    "result_url": "https://vidgen.x.ai/xai-vidgen-bucket/xai-video-c5e2b7b3-4ac3-9cb4-abd8-b6b70d761cb2.mp4",
                    "data": {
                      "model": "grok-imagine-video",
                      "status": "done",
                      "video": {
                        "url": "https://vidgen.x.ai/xai-vidgen-bucket/xai-video-c5e2b7b3-4ac3-9cb4-abd8-b6b70d761cb2.mp4"
                      }
                    }
                  }
                }
                """;
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        VideoGenerationStatus status =
                new New2ApiVideoProvider(cfg, httpClient).getGenerationStatus("task_GNasyg3jTfqYOUDr8crJDADNqEu5PbZI");

        assertThat(status.providerStatus()).isEqualTo("succeeded");
        assertThat(status.videoUrl())
                .isEqualTo("https://vidgen.x.ai/xai-vidgen-bucket/xai-video-c5e2b7b3-4ac3-9cb4-abd8-b6b70d761cb2.mp4");
        assertThat(status.providerJobId()).isEqualTo("task_GNasyg3jTfqYOUDr8crJDADNqEu5PbZI");
    }

    private static VideoProviderProperties.New2api baseCfg() {
        VideoProviderProperties.New2api cfg = new VideoProviderProperties.New2api();
        cfg.setApiKey("k");
        cfg.setBaseUrl("http://new2api.example/v1");
        cfg.setModel("video");
        return cfg;
    }
}
