package com.yunpei.proxy.infra.oss;

import com.yunpei.proxy.service.image.TransferResult;
import com.yunpei.proxy.service.video.VideoProviderException;
import com.yunpei.proxy.service.video.VideoStorage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 将 Provider 返回的视频 URL 下载并上传到 S3。
 *
 * <p>key 形如 {@code {prefix}external-video/{jobId}.mp4}（扩展名按 content-type / URL 推断）。
 * <b>不</b>复用 {@code S3ImageStorage}，避免落到 {@code external-image/} 且被猜成 {@code .png}。
 *
 * @author yunpei
 */
@Component
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "true")
public class S3VideoStorage implements VideoStorage {

    private static final Logger log = LoggerFactory.getLogger(S3VideoStorage.class);

    private final S3Client s3Client;
    private final OssProperties props;
    private final HttpClient httpClient;

    @Autowired
    public S3VideoStorage(S3Client s3Client, OssProperties props) {
        this(s3Client, props, defaultHttpClient(props));
    }

    /** 单测可注入 mock HttpClient。 */
    public S3VideoStorage(S3Client s3Client, OssProperties props, HttpClient httpClient) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.props = Objects.requireNonNull(props, "props");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public TransferResult transfer(String sourceUrl, String jobId) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new VideoProviderException("source video url is blank");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new VideoProviderException("jobId is blank");
        }
        String source = sourceUrl.trim();
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            throw new VideoProviderException(
                    "unsupported video source scheme (expect http(s)): " + truncate(source, 64));
        }

        DownloadResult downloaded = downloadHttp(source);
        String contentType = guessContentType(source, downloaded.contentType(), downloaded.bytes());
        String ext = extensionForContentType(contentType, source);
        String key = props.getEffectiveKeyPrefix() + "external-video/" + jobId.trim() + ext;

        byte[] bytes = downloaded.bytes();
        try {
            S3PutObjects.put(s3Client, props, key, bytes, contentType);
        } catch (RuntimeException e) {
            log.error("S3 video putObject failed: key={}, size={}", key, bytes.length, e);
            throw new VideoProviderException("s3 putObject failed: " + describeThrowable(e), e);
        }

        String publicUrl = buildPublicUrl(key);
        if (publicUrl == null) {
            log.warn("OSS public-base-url is empty; falling back to provider video URL. key={}", key);
            publicUrl = source;
        }
        log.info("S3 video upload success: key={}, size={}, contentType={}", key, bytes.length, contentType);
        return new TransferResult(publicUrl, key);
    }

    private DownloadResult downloadHttp(String sourceUrl) {
        int timeoutSeconds = props.getDownloadTimeoutSeconds() <= 0 ? 120 : props.getDownloadTimeoutSeconds();
        // 视频文件可能较大，下载超时至少 120s
        timeoutSeconds = Math.max(timeoutSeconds, 120);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "java-admin-video-storage/1.0")
                .header("Accept", "video/*,*/*")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new VideoProviderException(
                        "download provider video HTTP " + response.statusCode() + " for " + truncate(sourceUrl, 120));
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new VideoProviderException("downloaded provider video is empty");
            }
            String responseContentType =
                    response.headers().firstValue("Content-Type").orElse(null);
            return new DownloadResult(body, responseContentType);
        } catch (VideoProviderException e) {
            throw e;
        } catch (IOException e) {
            log.error("download provider video failed: url={}", truncate(sourceUrl, 200), e);
            throw new VideoProviderException(
                    "download provider video failed: " + describeThrowable(e) + " url=" + truncate(sourceUrl, 120), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("download provider video interrupted", e);
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
                props == null || props.getDownloadTimeoutSeconds() <= 0 ? 120 : props.getDownloadTimeoutSeconds();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 推断视频 content-type：优先响应头 → URL 扩展名 → 魔数 → 默认 video/mp4。
     */
    static String guessContentType(String sourceUrl, String responseContentType, byte[] bytes) {
        if (responseContentType != null && !responseContentType.isBlank()) {
            String mime = responseContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (mime.startsWith("video/")) {
                return mime;
            }
        }
        String fromPath = contentTypeFromPath(sourceUrl);
        if (fromPath != null) {
            return fromPath;
        }
        if (looksLikeMp4(bytes)) {
            return "video/mp4";
        }
        if (looksLikeWebm(bytes)) {
            return "video/webm";
        }
        // Provider 视频默认按 mp4 存，绝不回落到 image/png
        return "video/mp4";
    }

    static String extensionForContentType(String contentType, String sourceUrl) {
        if (contentType != null) {
            String ext =
                    switch (contentType.toLowerCase(Locale.ROOT)) {
                        case "video/webm" -> ".webm";
                        case "video/quicktime" -> ".mov";
                        case "video/x-msvideo", "video/avi" -> ".avi";
                        case "video/mp4", "video/mpeg" -> ".mp4";
                        default -> null;
                    };
            if (ext != null) {
                return ext;
            }
        }
        String pathExt = pathExtension(sourceUrl);
        if (pathExt != null) {
            return pathExt;
        }
        return ".mp4";
    }

    private static String contentTypeFromPath(String sourceUrl) {
        String path = pathWithoutQuery(sourceUrl).toLowerCase(Locale.ROOT);
        if (path.endsWith(".webm")) {
            return "video/webm";
        }
        if (path.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (path.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mpeg") || path.endsWith(".mpg")) {
            return "video/mp4";
        }
        return null;
    }

    private static String pathExtension(String sourceUrl) {
        String path = pathWithoutQuery(sourceUrl).toLowerCase(Locale.ROOT);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot);
        return switch (ext) {
            case ".mp4", ".m4v", ".webm", ".mov", ".avi", ".mpeg", ".mpg" -> ext.equals(".m4v") ? ".mp4" : ext;
            default -> null;
        };
    }

    private static String pathWithoutQuery(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        String noQuery = q >= 0 ? url.substring(0, q) : url;
        int h = noQuery.indexOf('#');
        return h >= 0 ? noQuery.substring(0, h) : noQuery;
    }

    /** ISO BMFF / MP4：offset 4 起 "ftyp"。 */
    private static boolean looksLikeMp4(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        return bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    /** EBML / WebM 开头 0x1A 0x45 0xDF 0xA3。 */
    private static boolean looksLikeWebm(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        return (bytes[0] & 0xFF) == 0x1A
                && (bytes[1] & 0xFF) == 0x45
                && (bytes[2] & 0xFF) == 0xDF
                && (bytes[3] & 0xFF) == 0xA3;
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

    private record DownloadResult(byte[] bytes, String contentType) {}
}
