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
 * new2api 视频 Provider：OpenAI 兼容异步 {@code POST /videos} + {@code GET /videos/{id}}。
 *
 * @author yunpei
 */
public class New2ApiVideoProvider implements VideoProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(New2ApiVideoProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final List<Integer> retryDelaysSeconds;

    public New2ApiVideoProvider(VideoProviderProperties.New2api properties) {
        this(properties, defaultHttpClient(properties));
    }

    public New2ApiVideoProvider(VideoProviderProperties.New2api properties, HttpClient httpClient) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(httpClient, "httpClient");
        String key = trimToNull(properties.getApiKey());
        if (key == null) {
            throw new VideoProviderException(
                    "NEW2API_API_KEY or VIDEO_API_KEY is required when video.provider=new2api");
        }
        String base = trimToNull(properties.getBaseUrl());
        if (base == null) {
            throw new VideoProviderException("video.new2api.base-url is required");
        }
        String modelId = trimToNull(properties.getModel());
        if (modelId == null) {
            throw new VideoProviderException("video.new2api.model is required");
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
        body.put("seconds", String.valueOf(Math.max(1, request.durationSeconds())));
        if (request.aspectRatio() != null && !request.aspectRatio().isBlank()) {
            body.put("size", aspectRatioToSize(request.aspectRatio()));
        }
        List<VideoSourceImage> images = request.sourceImages() == null ? List.of() : request.sourceImages();
        if (!images.isEmpty()) {
            ArrayNode refs = body.putArray("input_reference");
            for (VideoSourceImage image : images) {
                if (image == null || image.url() == null || image.url().isBlank()) {
                    continue;
                }
                ObjectNode ref = refs.addObject();
                ref.put("image_url", image.url());
                if (image.role() != null) {
                    ref.put("role", image.role());
                }
            }
        }

        String requestJson;
        try {
            requestJson = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new VideoProviderException("failed to serialize new2api video request", e);
        }

        HttpResponse<String> response = exchange(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/video/generations"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build());

        JsonNode root = parseJson(response.body());
        String id = text(root, "id");
        if (id == null) {
            throw new VideoProviderException(
                    "new2api video create returned no id; body=" + truncate(response.body(), 200));
        }
        String status = text(root, "status");
        if (status == null) {
            status = "queued";
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", id);
        raw.put("status", status);
        raw.put("provider", "new2api");
        return new VideoGenerationStart(id, normalizeStatus(status), raw);
    }

    @Override
    public VideoGenerationStatus getGenerationStatus(String providerJobId) {
        String id = trimToNull(providerJobId);
        if (id == null) {
            throw new VideoProviderException("providerJobId is required");
        }
        HttpResponse<String> response = exchange(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/video/generations/" + id))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build());
        JsonNode root = parseJson(response.body());
        JsonNode payload = unwrapTaskPayload(root);
        String status = text(payload, "status");
        if (status == null) {
            status = "processing";
        }
        String normalized = normalizeStatus(status);
        String videoUrl = extractVideoUrl(root);
        String error = text(payload, "error");
        if (error == null
                && payload != null
                && payload.has("error")
                && payload.get("error").isObject()) {
            error = text(payload.get("error"), "message");
        }
        if (error == null) {
            error = text(payload, "fail_reason");
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", id);
        raw.put("status", status);
        raw.put("provider", "new2api");
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
                                "new2api video API failed after {}/{} attempts {} {}: {}",
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
                        "new2api video API failed (attempt={}/{}) {} {}: {}; retry in {}s",
                        attempt,
                        attempts,
                        request.method(),
                        request.uri(),
                        e.getMessage(),
                        seconds);
                sleepRetry(attempt);
            }
        }
        throw last == null ? new VideoProviderException("new2api video API failed") : last;
    }

    private HttpResponse<String> exchangeOnce(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // JDK 网络异常常见 getMessage()==null（ClosedChannelException 等），必须带上类型名
            throw new VideoProviderException(
                    "new2api video API request failed: " + describeThrowable(e) + " " + request.method() + " "
                            + request.uri(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("new2api video API request interrupted", e);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response;
        }
        throw new VideoProviderException(
                "new2api video API HTTP " + response.statusCode() + ": " + truncate(response.body(), 256),
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
            throw new VideoProviderException("new2api video API retry sleep interrupted", e);
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

    /**
     * new2api 任务查询可能包一层 {@code { code, data: { ...task } }}；任务字段在 {@code data} 上。
     */
    static JsonNode unwrapTaskPayload(JsonNode root) {
        if (root == null || !root.has("data") || !root.get("data").isObject()) {
            return root;
        }
        JsonNode data = root.get("data");
        if (data.has("status") || data.has("task_id") || data.has("result_url") || data.has("id")) {
            return data;
        }
        return root;
    }

    /**
     * 从查询响应中解析视频 URL。
     *
     * <p>兼容形态（按优先级）：
     * <ul>
     *   <li>顶层 {@code url} / {@code video_url} / {@code result_url}</li>
     *   <li>{@code video.url}（xAI grok-imagine-video 完成态）</li>
     *   <li>{@code metadata.url}（new2api / 豆包 Seedance 完成态常见）</li>
     *   <li>{@code content|output|result|data} 对象内的 url 字段</li>
     *   <li>{@code data.data.video.url}（new2api 任务查询 envelope）</li>
     *   <li>{@code data[]} 首元素及其嵌套 url</li>
     * </ul>
     */
    static String extractVideoUrl(JsonNode root) {
        return extractVideoUrl(root, 0);
    }

    private static String extractVideoUrl(JsonNode root, int depth) {
        if (root == null || root.isNull() || depth > 4) {
            return null;
        }
        String url = firstUrlField(root);
        if (url != null) {
            return url;
        }
        for (String container : List.of("video", "metadata", "content", "output", "result", "data")) {
            JsonNode node = root.get(container);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isObject()) {
                url = extractVideoUrl(node, depth + 1);
                if (url != null) {
                    return url;
                }
            } else if (node.isArray() && !node.isEmpty()) {
                JsonNode first = node.get(0);
                if (first != null && first.isObject()) {
                    url = extractVideoUrl(first, depth + 1);
                    if (url != null) {
                        return url;
                    }
                }
            }
        }
        return null;
    }

    /** 读取节点上的 url / video_url / result_url（非空字符串）。 */
    private static String firstUrlField(JsonNode node) {
        for (String field : List.of("url", "video_url", "result_url")) {
            String url = text(node, field);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static String normalizeStatus(String status) {
        String s = status == null ? "" : status.trim().toLowerCase();
        return switch (s) {
            case "completed", "complete", "succeeded", "success", "done" -> "succeeded";
            case "failed", "error", "cancelled", "canceled" -> "failed";
            default -> "processing";
        };
    }

    private static String aspectRatioToSize(String aspectRatio) {
        String ar = aspectRatio.trim();
        if ("9:16".equals(ar)) {
            return "720x1280";
        }
        if ("16:9".equals(ar)) {
            return "1280x720";
        }
        if ("1:1".equals(ar)) {
            return "1024x1024";
        }
        return ar;
    }

    private static JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body == null ? "{}" : body);
        } catch (IOException e) {
            throw new VideoProviderException("failed to parse new2api video response", e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static HttpClient defaultHttpClient(VideoProviderProperties.New2api properties) {
        int timeoutSeconds = properties.getTimeoutSeconds() <= 0 ? 120 : properties.getTimeoutSeconds();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
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
