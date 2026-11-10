package com.yunpei.proxy.infra.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.service.image.TransferResult;
import com.yunpei.proxy.service.video.VideoProviderException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@link S3VideoStorage} 契约：external-video 前缀 + .mp4，不走 external-image/.png。
 */
class S3VideoStorageTest {

    @Test
    @SuppressWarnings("unchecked")
    void transfer_uploadsAsMp4UnderExternalVideo() throws Exception {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setBucket("demo-bucket");
        props.setPublicBaseUrl("https://cdn.example");
        props.setKeyPrefix("advertising");

        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // ftyp 魔数（MP4）
        byte[] mp4 = new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(mp4);
        when(response.headers())
                .thenReturn(HttpHeaders.of(Map.of("Content-Type", java.util.List.of("video/mp4")), (a, b) -> true));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        S3VideoStorage storage = new S3VideoStorage(s3, props, http);
        String providerUrl =
                "https://ark-acg-cn-beijing.tos-cn-beijing.volces.com/doubao-seedance-2-0-mini/x.mp4?X-Tos-Signature=abc";
        TransferResult result = storage.transfer(providerUrl, "6db7810f-d3b8-4968-b43c-783dd1afd365");

        assertThat(result.storageKey())
                .isEqualTo("advertising/external-video/6db7810f-d3b8-4968-b43c-783dd1afd365.mp4");
        assertThat(result.publicUrl())
                .isEqualTo("https://cdn.example/advertising/external-video/6db7810f-d3b8-4968-b43c-783dd1afd365.mp4");
        assertThat(result.storageKey()).doesNotContain("external-image");
        assertThat(result.storageKey()).doesNotEndWith(".png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(captor.getValue().key())
                .isEqualTo("advertising/external-video/6db7810f-d3b8-4968-b43c-783dd1afd365.mp4");
    }

    @Test
    void guessContentType_defaultsToMp4NotPng() {
        assertThat(S3VideoStorage.guessContentType("https://x.example/a", null, new byte[] {1, 2, 3}))
                .isEqualTo("video/mp4");
        assertThat(S3VideoStorage.extensionForContentType("video/mp4", "https://x.example/a"))
                .isEqualTo(".mp4");
    }

    @Test
    void guessContentType_fromPathWithQuery() {
        String url = "https://ark.example/doubao/sample.mp4?X-Tos-Algorithm=TOS4-HMAC-SHA256&X-Tos-Signature=x";
        assertThat(S3VideoStorage.guessContentType(url, null, new byte[] {0})).isEqualTo("video/mp4");
        assertThat(S3VideoStorage.extensionForContentType(null, url)).isEqualTo(".mp4");
    }

    @Test
    void transfer_blankSource_throws() {
        S3VideoStorage storage = new S3VideoStorage(mock(S3Client.class), new OssProperties(), mock(HttpClient.class));
        assertThatThrownBy(() -> storage.transfer(" ", "job-1"))
                .isInstanceOf(VideoProviderException.class)
                .hasMessageContaining("blank");
    }
}
