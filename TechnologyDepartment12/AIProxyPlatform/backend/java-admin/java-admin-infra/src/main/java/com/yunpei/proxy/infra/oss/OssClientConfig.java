package com.yunpei.proxy.infra.oss;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * S3Client 装配（仅 {@code oss.enabled=true}）。
 *
 * <p>对齐 mini_game_traffic {@code OssClientConfig}：endpoint 覆盖、path-style、静态 AK/SK。
 *
 * @author yunpei
 */
@Configuration
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "true")
public class OssClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OssClientConfig.class);

    /**
     * @param props OSS 配置
     * @return 可关闭的 S3Client
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(OssProperties props) {
        validate(props);
        S3Client client = S3Client.builder()
                .region(Region.of(blankToDefault(props.getRegion(), "ap-east-1")))
                .endpointOverride(URI.create(props.getEndpoint().trim()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        props.getAccessKey().trim(), props.getSecretKey().trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyle())
                        .build())
                .build();
        log.info(
                "OSS S3Client initialized: endpoint={}, bucket={}, region={}",
                props.getEndpoint(),
                props.getBucket(),
                props.getRegion());
        return client;
    }

    private static void validate(OssProperties props) {
        if (isBlank(props.getEndpoint())
                || isBlank(props.getBucket())
                || isBlank(props.getAccessKey())
                || isBlank(props.getSecretKey())) {
            throw new IllegalStateException(
                    "OSS config incomplete: endpoint/bucket/accessKey/secretKey are all required when oss.enabled=true");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "REPLACE_ME".equalsIgnoreCase(s);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }
}
