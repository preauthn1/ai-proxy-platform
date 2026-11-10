package com.yunpei.proxy.infra.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link S3PutObjects}：默认不带 ACL；public-read 失败后进程内只再试一次无 ACL。
 */
class S3PutObjectsTest {

    @BeforeEach
    @AfterEach
    void resetAclSkip() {
        S3PutObjects.resetPublicReadAclSkipForTest();
    }

    @Test
    void put_defaultNone_sendsOnceWithoutAcl() {
        OssProperties props = baseProps();
        S3Client s3 = mockSuccess();

        S3PutObjects.put(s3, props, "k.jpg", new byte[] {1, 2}, "image/jpeg");

        PutObjectRequest req = captureOnce(s3);
        assertThat(req.acl()).isNull();
        assertThat(req.key()).isEqualTo("k.jpg");
        assertThat(req.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void put_publicReadSuccess_sendsOnceWithAcl() {
        OssProperties props = baseProps();
        props.setObjectAcl("public-read");
        S3Client s3 = mockSuccess();

        S3PutObjects.put(s3, props, "k.jpg", new byte[] {1}, "image/jpeg");

        assertThat(captureOnce(s3).acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
    }

    @Test
    void put_publicReadRejected_retriesWithoutAclThenSkipsAcl() {
        OssProperties props = baseProps();
        props.setObjectAcl("public-read");
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder()
                        .message("The bucket does not allow ACLs")
                        .statusCode(400)
                        .build())
                .thenReturn(PutObjectResponse.builder().build())
                .thenReturn(PutObjectResponse.builder().build());

        S3PutObjects.put(s3, props, "first.jpg", new byte[] {1}, "image/jpeg");
        S3PutObjects.put(s3, props, "second.jpg", new byte[] {2}, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(3)).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getAllValues().get(0).acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
        assertThat(captor.getAllValues().get(1).acl()).isNull();
        assertThat(captor.getAllValues().get(1).key()).isEqualTo("first.jpg");
        assertThat(captor.getAllValues().get(2).acl()).isNull();
        assertThat(captor.getAllValues().get(2).key()).isEqualTo("second.jpg");
    }

    @Test
    void put_none_doesNotRetryWhenPutFails() {
        OssProperties props = baseProps();
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("boom").statusCode(500).build());

        assertThatThrownBy(() -> S3PutObjects.put(s3, props, "k.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(S3Exception.class);

        verify(s3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private static OssProperties baseProps() {
        OssProperties props = new OssProperties();
        props.setBucket("demo-bucket");
        return props;
    }

    private static S3Client mockSuccess() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        return s3;
    }

    private static PutObjectRequest captureOnce(S3Client s3) {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(1)).putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }
}
