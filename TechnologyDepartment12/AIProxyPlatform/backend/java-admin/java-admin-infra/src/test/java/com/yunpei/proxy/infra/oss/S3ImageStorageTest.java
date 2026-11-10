package com.yunpei.proxy.infra.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.service.image.ImageProviderException;
import com.yunpei.proxy.service.image.TransferResult;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@link S3ImageStorage} 契约测试（mock S3Client + HttpClient）。
 */
class S3ImageStorageTest {

    @Test
    @SuppressWarnings("unchecked")
    void transfer_uploadsAndBuildsPublicUrl() throws Exception {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setBucket("demo-bucket");
        props.setPublicBaseUrl("https://cdn.example");
        props.setKeyPrefix("java-admin");

        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        // minimal PNG magic-ish payload
        when(response.body()).thenReturn(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A});
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        S3ImageStorage storage = new S3ImageStorage(s3, props, http);
        TransferResult result = storage.transfer("https://provider.example/a.png", "job-abc", 2);

        assertThat(result.storageKey()).isEqualTo("java-admin/external-image/job-abc/2.png");
        assertThat(result.publicUrl()).isEqualTo("https://cdn.example/java-admin/external-image/job-abc/2.png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("demo-bucket");
        assertThat(captor.getValue().key()).isEqualTo("java-admin/external-image/job-abc/2.png");
        assertThat(captor.getValue().acl()).isNull();
    }

    @Test
    void transfer_blankSource_throws() {
        S3ImageStorage storage = new S3ImageStorage(mock(S3Client.class), new OssProperties(), mock(HttpClient.class));
        assertThatThrownBy(() -> storage.transfer(" ", "job-1", 1))
                .isInstanceOf(ImageProviderException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void transfer_dataUrl_uploadsWithoutHttp() {
        OssProperties props = new OssProperties();
        props.setBucket("demo-bucket");
        props.setPublicBaseUrl("https://cdn.example");
        props.setKeyPrefix("java-admin");

        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // "hi" base64 = aGk=
        S3ImageStorage storage = new S3ImageStorage(s3, props, mock(HttpClient.class));
        TransferResult result = storage.transfer("data:image/png;base64,aGk=", "job-data", 1);

        assertThat(result.storageKey()).isEqualTo("java-admin/external-image/job-data/1.png");
        assertThat(result.publicUrl()).isEqualTo("https://cdn.example/java-admin/external-image/job-data/1.png");
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void storeUploadedSourceImage_usesSourcePrefix() {
        OssProperties props = new OssProperties();
        props.setBucket("demo-bucket");
        props.setPublicBaseUrl("https://cdn.example");
        props.setKeyPrefix("java-admin");

        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        S3ImageStorage storage = new S3ImageStorage(s3, props, mock(HttpClient.class));
        TransferResult result = storage.storeUploadedSourceImage(png, "image/png");

        assertThat(result.storageKey()).startsWith("java-admin/external-image-source/");
        assertThat(result.storageKey()).endsWith(".png");
        assertThat(result.publicUrl()).startsWith("https://cdn.example/java-admin/external-image-source/");
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
