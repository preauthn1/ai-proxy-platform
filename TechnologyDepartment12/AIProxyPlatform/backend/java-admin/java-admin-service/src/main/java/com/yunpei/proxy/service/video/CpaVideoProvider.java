package com.yunpei.proxy.service.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CPA 视频 Provider：异步 {@code POST /videos/generations} + {@code GET /videos/{id}}。
 *
 * <p>请求体仅发送 {@code model} / {@code prompt} / {@code reference_images[{url}]}；
 * 时长、宽高比、源图 role 不透传。状态与视频 URL 解析兼容 new2api 常见完成态形态。
 *
 * @author yunpei
 */
public class CpaVideoProvider implements VideoProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(CpaVideoProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final List<Integer> retryDelaysSeconds;

    public CpaVideoProvider(VideoProviderProperties.Cpa properties) {
        this(properties, defaultHttpClient(properties));
    }

    public CpaVideoProvider(VideoProviderProperties.Cpa properties, HttpClient httpClient) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(httpClient, "httpClient");
        String key = trimToNull(properties.getApiKey());
        if (key == null) {
            throw new VideoProviderException("CPA_API_KEY or VIDEO_CPA_API_KEY is required when video.provider=cpa");
        }
        String base = trimToNull(properties.getBaseUrl());
        if (base == null) {
            throw new VideoProviderException("video.cpa.base-url is required");
        }
        String modelId = trimToNull(properties.getModel());
        if (modelId == null) {
            throw new VideoProviderException("video.cpa.model is required");
        }
        this.apiKey = key;
        this.baseUrl = stripTrailingSlash(base);
        this.defaultModel = modelId;
        int timeoutSeconds = properties.getTimeoutSeconds() <= 0 ? 120 : properties.getTimeoutSeconds();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = httpClient;
        this.retryDelaysSeconds = normalizeRetryDelays(properties.getRetryDelaysSeconds());
    }

    @Override
    public VideoGenerationStart startGeneration(VideoGenerationRequest request) {
        Objects.requireNonNull(request, "request");
        String prompt = request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isEmpty()) {
            throw new VideoProviderException("prompt is required");
        }

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", defaultModel);
        body.put("prompt", prompt);
        List<VideoSourceImage> images = request.sourceImages() == null ? List.of() : request.sourceImages();
        if (!images.isEmpty()) {
            ArrayNode refs = body.putArray("reference_images");
            for (VideoSourceImage image : images) {
                if (image == null || image.url() == null || image.url().isBlank()) {
                    continue;
                }
                ObjectNode ref = refs.addObject();
                ref.put("url", image.url());
            }
            if (refs.isEmpty()) {
                body.remove("reference_images");
            }
        }

        String requestJson;
        try {
            requestJson = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new VideoProviderException("failed to serialize cpa video request", e);
        }

        HttpResponse<String> response = exchange(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/videos/generations"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build());

        JsonNode root = parseJson(response.body());
        String id = firstId(root);
        if (id == null) {
            throw new VideoProviderException("cpa video create returned no id; body=" + truncate(response.body(), 200));
        }
        String status = text(root, "status");
        if (status == null) {
            status = "queued";
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", id);
        raw.put("status", status);
        raw.put("provider", "cpa");
        return new VideoGenerationStart(id, normalizeStatus(status), raw);
    }

    @Override
    public VideoGenerationStatus getGenerationStatus(String providerJobId) {
        String id = trimToNull(providerJobId);
        if (id == null) {
            throw new VideoProviderException("providerJobId is required");
        }
        HttpResponse<String> response = exchange(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/videos/" + id))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .GET()
                .build());
        JsonNode root = parseJson(response.body());
        String status = text(root, "status");
        if (status == null) {
            status = "processing";
        }
        String normalized = normalizeStatus(status);
        String videoUrl = extractVideoUrl(root);
        String error = text(root, "error");
        if (error == null && root.has("error") && root.get("error").isObject()) {
            error = text(root.get("error"), "message");
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", id);
        raw.put("status", status);
        raw.put("provider", "cpa");
        if (videoUrl != null) {
            raw.put("video_url", videoUrl);
        }
        return new VideoGenerationStatus(id, normalized, videoUrl, error, raw);
    }

    private HttpResponse<String> exchange(HttpRequest request) {
        int attempts = 1 + retryDelaysSeconds.size();
        VideoProviderException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return exchangeOnce(request);
            } catch (VideoProviderException e) {
                last = e;
                boolean retryable = isRetryable(e);
                if (!retryable || attempt >= attempts) {
                    if (attempt > 1 || retryable) {
                        log.warn(
                                "cpa video API failed after {}/{} attempts {} {}: {}",
                                attempt,
                                attempts,
                                request.method(),
                                request.uri(),
                                e.getMessage());
                    }
                    throw e;
                }
                int delayIndex = attempt - 1;
                int seconds = delayIndex < retryDelaysSeconds.size() ? retryDelaysSeconds.get(delayIndex) : 0;
                log.warn(
                        "cpa video API failed (attempt={}/{}) {} {}: {}; retry in {}s",
                        attempt,
                        attempts,
                        request.method(),
                        request.uri(),
                        e.getMessage(),
                        seconds);
                sleepRetry(attempt);
            }
        }
        throw last == null ? new VideoProviderException("cpa video API failed") : last;
    }

    private HttpResponse<String> exchangeOnce(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // JDK 网络异常常见 getMessage()==null（ClosedChannelException 等），必须带上类型名
            throw new VideoProviderException(
                    "cpa video API request failed: " + describeThrowable(e)
                            + " " + request.method() + " " + request.uri()
                            + connectivityHint(e, request.uri()),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("cpa video API request interrupted", e);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response;
        }
        throw new VideoProviderException(
                "cpa video API HTTP " + response.statusCode() + ": " + truncate(response.body(), 256),
                response.statusCode());
    }

    /** 仅网络 IO / 5xx 可重试；4xx 与业务错误立即失败。 */
    private static boolean isRetryable(VideoProviderException e) {
        if (e.getCause() instanceof IOException) {
            return true;
        }
        int status = e.httpStatus();
        return status >= 500 && status <= 599;
    }

    private void sleepRetry(int attempt) {
        int delayIndex = attempt - 1;
        if (delayIndex < 0 || delayIndex >= retryDelaysSeconds.size()) {
            return;
        }
        int seconds = retryDelaysSeconds.get(delayIndex);
        if (seconds <= 0) {
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("cpa video API retry sleep interrupted", e);
        }
    }

    /**
     * 格式化异常链：类型名 + message（message 为空时仍保留类型，避免线上只见 {@code null}）。
     */
    static String describeThrowable(Throwable t) {
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

    /** 连接类失败时补充可操作提示（CPA 上游宕机/防火墙常见）。 */
    static String connectivityHint(Throwable t, URI uri) {
        if (!isConnectivityFailure(t) || uri == null) {
            return "";
        }
        String host = uri.getHost() == null ? "?" : uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        return " | cannot reach " + host + ":" + port
                + " (check CPA process/firewall, or set VIDEO_PROVIDER=new2api / CPA_BASE_URL)";
    }

    static boolean isConnectivityFailure(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            String name = cur.getClass().getSimpleName();
            if (name.contains("ConnectException")
                    || name.contains("ClosedChannelException")
                    || name.contains("HttpConnectTimeoutException")
                    || name.contains("NoRouteToHostException")
                    || name.contains("UnknownHostException")
                    || name.contains("UnresolvedAddressException")) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    /**
     * 从查询响应中解析视频 URL。
     *
     * <p>兼容形态（按优先级）：
     * <ul>
     *   <li>顶层 {@code url} / {@code video_url}</li>
     *   <li>{@code video.url}（CPA / xAI grok-imagine-video 完成态）</li>
     *   <li>{@code metadata|content|output|result|data} 内的 url / video_url</li>
     * </ul>
     */
    static String extractVideoUrl(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        String url = firstUrlField(root);
        if (url != null) {
            return url;
        }
        // CPA 完成态：{ status: done, video: { url: "https://...mp4" } }
        for (String container : List.of("video", "metadata", "content", "output", "result", "data")) {
            JsonNode node = root.get(container);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isObject()) {
                url = firstUrlField(node);
                if (url != null) {
                    return url;
                }
            } else if (node.isArray() && !node.isEmpty()) {
                JsonNode first = node.get(0);
                if (first != null && first.isObject()) {
                    url = firstUrlField(first);
                    if (url != null) {
                        return url;
                    }
                    JsonNode nestedMeta = first.get("metadata");
                    if (nestedMeta != null && nestedMeta.isObject()) {
                        url = firstUrlField(nestedMeta);
                        if (url != null) {
                            return url;
                        }
                    }
                    JsonNode nestedVideo = first.get("video");
                    if (nestedVideo != null && nestedVideo.isObject()) {
                        url = firstUrlField(nestedVideo);
                        if (url != null) {
                            return url;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String firstUrlField(JsonNode node) {
        String url = text(node, "url");
        if (url != null) {
            return url;
        }
        return text(node, "video_url");
    }

    /**
     * 创建响应任务 id 字段（按优先级）：{@code id} → {@code task_id} → {@code request_id}。
     *
     * <p>CPA {@code POST /videos/generations} 实测完成排队时常仅返回 {@code request_id}。
     */
    private static String firstId(JsonNode root) {
        String id = text(root, "id");
        if (id != null) {
            return id;
        }
        id = text(root, "task_id");
        if (id != null) {
            return id;
        }
        return text(root, "request_id");
    }

    private static String normalizeStatus(String status) {
        String s = status == null ? "" : status.trim().toLowerCase();
        return switch (s) {
            case "completed", "complete", "succeeded", "success", "done" -> "succeeded";
            case "failed", "error", "cancelled", "canceled" -> "failed";
            default -> "processing";
        };
    }

    private static JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body == null ? "{}" : body);
        } catch (IOException e) {
            throw new VideoProviderException("failed to parse cpa video response", e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static HttpClient defaultHttpClient(VideoProviderProperties.Cpa properties) {
        int timeoutSeconds = properties.getTimeoutSeconds() <= 0 ? 120 : properties.getTimeoutSeconds();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                // 对 IP/代理类接入点更稳，避免 HTTP/2 半开连接触发无 message 的 IOException
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private static List<Integer> normalizeRetryDelays(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Integer n : raw) {
            if (n != null && n >= 0) {
                out.add(n);
            }
        }
        return List.copyOf(out);
    }

    private static String stripTrailingSlash(String base) {
        String b = base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
