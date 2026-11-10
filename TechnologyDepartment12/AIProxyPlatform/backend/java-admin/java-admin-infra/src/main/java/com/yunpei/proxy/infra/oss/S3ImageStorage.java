package com.yunpei.proxy.infra.oss;

import com.yunpei.proxy.service.image.ImageContentType;
import com.yunpei.proxy.service.image.ImageContentTypes;
import com.yunpei.proxy.service.image.ImageProviderException;
import com.yunpei.proxy.service.image.ImageStorage;
import com.yunpei.proxy.service.image.TransferResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * 将 Provider 返回的图片（http(s) URL 或 data URL）上传到 S3，返回公开 URL。
 *
 * <p>key 形如 {@code {prefix}external-image/{jobId}/{index}.png}；对齐 mini_game 的 putObject + publicBaseUrl。
 *
 * @author yunpei
 */
@Component
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "true")
public class S3ImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);

    private final S3Client s3Client;
    private final OssProperties props;
    private final HttpClient httpClient;

    /**
     * Spring 注入主构造：仅依赖 {@link S3Client} + {@link OssProperties}，内部自建 {@link HttpClient}。
     *
     * <p>多构造时必须显式 {@link Autowired}，否则 Spring 会报 No default constructor found。
     */
    @Autowired
    public S3ImageStorage(S3Client s3Client, OssProperties props) {
        this(s3Client, props, defaultHttpClient(props));
    }

    /**
     * 单测构造：可注入 mock {@link HttpClient}。
     *
     * @param s3Client S3 客户端
     * @param props OSS 配置
     * @param httpClient 下载用 HTTP 客户端
     */
    public S3ImageStorage(S3Client s3Client, OssProperties props, HttpClient httpClient) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.props = Objects.requireNonNull(props, "props");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public TransferResult transfer(String sourceUrl, String jobId, int index) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ImageProviderException("source image url is blank");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new ImageProviderException("jobId is blank");
        }
        int safeIndex = Math.max(index, 1);
        String source = sourceUrl.trim();
        byte[] bytes = resolveBytes(source);
        String contentType = guessContentType(source, bytes);
        String ext = extensionForContentType(contentType);
        String key = props.getEffectiveKeyPrefix() + "external-image/" + jobId.trim() + "/" + safeIndex + ext;

        putBytes(key, bytes, contentType);

        String publicUrl = buildPublicUrl(key);
        if (publicUrl == null) {
            log.warn("OSS public-base-url is empty; job will not have a stable public URL. key={}", key);
        }
        log.info("S3 image upload success: key={}, size={}, contentType={}", key, bytes.length, contentType);
        // 无 publicBaseUrl 时不回退 data: URL（会撑爆响应）；回退 http(s) 源链
        String resultUrl = publicUrl;
        if (resultUrl == null && !source.startsWith("data:")) {
            resultUrl = source;
        }
        if (resultUrl == null) {
            throw new ImageProviderException(
                    "s3 upload ok but OSS_PUBLIC_BASE_URL is empty; cannot build public URL for key=" + key);
        }
        return new TransferResult(resultUrl, key);
    }

    @Override
    public TransferResult storeUploadedSourceImage(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new ImageProviderException("Uploaded source image is empty.");
        }
        ImageContentType detected = ImageContentTypes.detect(data);
        String mime = contentType == null || contentType.isBlank() ? detected.mimeType() : contentType.trim();
        String ext = detected.extension();
        String key = props.getEffectiveKeyPrefix() + "external-image-source/" + UUID.randomUUID() + ext;
        putBytes(key, data, mime);
        String publicUrl = buildPublicUrl(key);
        if (publicUrl == null) {
            throw new ImageProviderException(
                    "s3 upload ok but OSS_PUBLIC_BASE_URL is empty; cannot build public URL for key=" + key);
        }
        log.info("S3 uploaded source image: key={}, size={}, contentType={}", key, data.length, mime);
        return new TransferResult(publicUrl, key);
    }

    @Override
    public String dataUrlForStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        // placeholder key 不会出现在本实现；仅处理本组件写入的 S3 key
        if (storageKey.startsWith("placeholder://") || storageKey.startsWith("local://")) {
            return null;
        }
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(storageKey)
                    .build());
            byte[] bytes = response.asByteArray();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String mime = response.response().contentType();
            if (mime == null || mime.isBlank()) {
                try {
                    mime = ImageContentTypes.detect(bytes).mimeType();
                } catch (ImageProviderException e) {
                    mime = "application/octet-stream";
                }
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (RuntimeException e) {
            log.warn("S3 getObject failed for dataUrl: key={}", storageKey, e);
            return null;
        }
    }

    private void putBytes(String key, byte[] bytes, String contentType) {
        try {
            S3PutObjects.put(s3Client, props, key, bytes, contentType);
        } catch (RuntimeException e) {
            log.error("S3 putObject failed: key={}, size={}", key, bytes.length, e);
            throw new ImageProviderException("s3 putObject failed: " + describeThrowable(e), e);
        }
    }

    private byte[] resolveBytes(String source) {
        if (source.regionMatches(true, 0, "data:", 0, 5)) {
            return decodeDataUrl(source);
        }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return downloadHttp(source);
        }
        throw new ImageProviderException(
                "unsupported image source scheme (expect http(s) or data:): " + truncate(source, 64));
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new ImageProviderException("invalid data URL: missing comma");
        }
        String meta = dataUrl.substring(0, comma);
        String payload = dataUrl.substring(comma + 1);
        if (payload.isBlank()) {
            throw new ImageProviderException("invalid data URL: empty payload");
        }
        try {
            if (meta.toLowerCase(Locale.ROOT).contains(";base64")) {
                return Base64.getDecoder().decode(payload);
            }
            // 非 base64 data URL（极少见）按 UTF-8 原始字节
            return java.net.URLDecoder.decode(payload, java.nio.charset.StandardCharsets.UTF_8)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ImageProviderException("invalid data URL base64: " + e.getMessage(), e);
        }
    }

    private byte[] downloadHttp(String sourceUrl) {
        int timeoutSeconds = props.getDownloadTimeoutSeconds() <= 0 ? 60 : props.getDownloadTimeoutSeconds();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "java-admin-image-storage/1.0")
                .header("Accept", "image/*,*/*")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ImageProviderException(
                        "download provider image HTTP " + response.statusCode() + " for " + truncate(sourceUrl, 120));
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new ImageProviderException("downloaded provider image is empty");
            }
            return body;
        } catch (ImageProviderException e) {
            throw e;
        } catch (IOException e) {
            log.error("download provider image failed: url={}", truncate(sourceUrl, 200), e);
            throw new ImageProviderException(
                    "download provider image failed: " + describeThrowable(e) + " url=" + truncate(sourceUrl, 120), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageProviderException("download provider image interrupted", e);
        }
    }

    private String buildPublicUrl(String key) {
        String base = props.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base.trim();
        return trimmed + "/" + key;
    }

    private static HttpClient defaultHttpClient(OssProperties props) {
        int timeoutSeconds =
                props == null || props.getDownloadTimeoutSeconds() <= 0 ? 60 : props.getDownloadTimeoutSeconds();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String guessContentType(String sourceUrl, byte[] bytes) {
        if (sourceUrl.regionMatches(true, 0, "data:", 0, 5)) {
            int semi = sourceUrl.indexOf(';');
            int comma = sourceUrl.indexOf(',');
            int end = semi > 5 ? semi : (comma > 5 ? comma : -1);
            if (end > 5) {
                String mime = sourceUrl.substring(5, end).trim().toLowerCase(Locale.ROOT);
                if (!mime.isEmpty()) {
                    return mime;
                }
            }
        }
        String lower = sourceUrl.toLowerCase(Locale.ROOT);
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        if (bytes != null && bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        return "image/png";
    }

    private static String extensionForContentType(String contentType) {
        if (contentType == null) {
            return ".png";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String describeThrowable(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 5) {
            if (depth > 0) {
                sb.append(" caused by ");
            }
            sb.append(cur.getClass().getSimpleName());
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                sb.append(": ").append(cur.getMessage());
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }
}
