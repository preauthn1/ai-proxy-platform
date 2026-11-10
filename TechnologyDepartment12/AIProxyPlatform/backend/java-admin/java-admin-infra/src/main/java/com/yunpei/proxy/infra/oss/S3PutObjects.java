package com.yunpei.proxy.infra.oss;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 putObject：按 {@link OssProperties#isPublicReadAclEnabled()} 决定是否带 canned ACL。
 *
 * <p>桶禁用 ACL 时第一次 {@code public-read} 会失败；失败后进程内记住，后续只传一次。
 */
final class S3PutObjects {

    private static final Logger log = LoggerFactory.getLogger(S3PutObjects.class);

    private static final AtomicBoolean SKIP_PUBLIC_READ_ACL = new AtomicBoolean(false);

    private S3PutObjects() {}

    static void put(S3Client s3Client, OssProperties props, String key, byte[] bytes, String contentType) {
        if (shouldTryPublicReadAcl(props)) {
            try {
                s3Client.putObject(request(props, key, bytes, contentType, true), RequestBody.fromBytes(bytes));
                return;
            } catch (RuntimeException first) {
                SKIP_PUBLIC_READ_ACL.set(true);
                log.warn("S3 putObject with PUBLIC_READ ACL failed, retry without ACL: key={}", key);
            }
        }
        s3Client.putObject(request(props, key, bytes, contentType, false), RequestBody.fromBytes(bytes));
    }

    private static boolean shouldTryPublicReadAcl(OssProperties props) {
        return props.isPublicReadAclEnabled() && !SKIP_PUBLIC_READ_ACL.get();
    }

    private static PutObjectRequest request(
            OssProperties props, String key, byte[] bytes, String contentType, boolean publicRead) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length);
        if (publicRead) {
            builder.acl(ObjectCannedACL.PUBLIC_READ);
        }
        return builder.build();
    }

    /** 单测重置进程内「桶不支持 ACL」标记。 */
    static void resetPublicReadAclSkipForTest() {
        SKIP_PUBLIC_READ_ACL.set(false);
    }
}
