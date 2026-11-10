package com.yunpei.proxy.infra.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3 兼容对象存储配置（对应 {@code oss.*}）。
 *
 * <p>对齐 mini_game_traffic 的 {@code OssProperties} 字段命名；密钥仅环境变量注入，禁止写入仓库。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** 是否启用真实 S3 落盘；false 时使用 PlaceholderImageStorage。 */
    private boolean enabled = false;

    private String endpoint;
    private String region = "ap-east-1";
    private String bucket;
    private String accessKey;
    private String secretKey;
    private boolean pathStyle = true;
    private String publicBaseUrl;

    /**
     * 全局 key 前缀，例如 {@code java-admin/} 或 {@code minigame/}。
     * 配置值允许带/不带斜杠，{@link #getEffectiveKeyPrefix()} 会归一化。
     */
    private String keyPrefix = "java-admin";

    /** HTTP 下载 provider 临时图的超时秒数。 */
    private int downloadTimeoutSeconds = 60;

    /**
     * putObject canned ACL：{@code none}（默认）或 {@code public-read}。
     *
     * <p>AWS 新桶默认 Bucket owner enforced，禁止对象 ACL；此时必须用 {@code none}，
     * 公开读改走桶策略 / CDN。设为 {@code public-read} 时若被桶拒绝，进程内后续上传不再带 ACL。
     */
    private String objectAcl = "none";

    /**
     * 是否在 putObject 时尝试 {@code public-read} canned ACL。
     *
     * @return {@code object-acl=public-read} 时为 true
     */
    public boolean isPublicReadAclEnabled() {
        return objectAcl != null && "public-read".equalsIgnoreCase(objectAcl.trim());
    }

    /**
     * 归一化后的前缀：去前导斜杠，补尾随斜杠；空值返回空串。
     *
     * @return 有效 key 前缀
     */
    public String getEffectiveKeyPrefix() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "";
        }
        String trimmed = keyPrefix.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.endsWith("/")) {
            trimmed = trimmed + "/";
        }
        return trimmed;
    }
}
