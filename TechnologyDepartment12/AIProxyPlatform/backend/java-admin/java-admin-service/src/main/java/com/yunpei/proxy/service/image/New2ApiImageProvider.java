package com.yunpei.proxy.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * new2api 图片生成 Provider（OpenAI 兼容 {@code /images/generations} + 可选 {@code /images/edits}）。
 *
 * <p>固定请求 {@code response_format=url}，由 {@link ImageStorage} HTTP 下载后落盘。
 * size 将宽高比映射为 Seedream 2K 像素（如 {@code 9:16} → {@code 1440x2560}）；
 * 已是 {@code 宽x高} 或未知值则原样透传。model 优先 brief，否则配置默认。
 * brief 含参考图且 {@code edit-enabled} 时走 multipart edit。
 *
 * <p>失败重试见 {@code image.new2api.retry-delays-seconds}。进程内并发由 Temporal
 * {@code spring.temporal.workers} 中 {@code image-generation} 队列 capacity 控制。
 *
 * @author yunpei
 */
public class New2ApiImageProvider implements ImageProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(New2ApiImageProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final HttpClient httpClient;
    /**
     * 失败后的等待秒数（按顺序）；长度为 N 时最多尝试 1+N 次。
     * 空列表表示不重试。
     */
    private final List<Integer> retryDelaysSeconds;

    private final boolean editEnabled;
    private final String editPath;
    private final String editModel;

    /**
     * @param properties new2api 配置
     */
    public New2ApiImageProvider(ImageProviderProperties.New2api properties) {
        this(properties, defaultHttpClient(properties));
    }

    /**
     * 可注入 {@link HttpClient} 便于单测。
     *
     * @param properties new2api 配置
     * @param httpClient HTTP 客户端
     */
    public New2ApiImageProvider(ImageProviderProperties.New2api properties, HttpClient httpClient) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(httpClient, "httpClient");
        String key = trimToNull(properties.getApiKey());
        if (key == null) {
            throw new ImageProviderException(
                    "NEW2API_API_KEY or IMAGE_API_KEY is required when image.provider=new2api");
        }
        String base = trimToNull(properties.getBaseUrl());
        if (base == null) {
            throw new ImageProviderException("image.new2api.base-url is required");
        }
        String modelId = trimToNull(properties.getModel());
        if (modelId == null) {
            throw new ImageProviderException("image.new2api.model is required");
        }
        this.apiKey = key;
        this.baseUrl = stripTrailingSlash(base);
        this.defaultModel = modelId;
        int timeoutSeconds = properties.getTimeoutSeconds() <= 0 ? 120 : properties.getTimeoutSeconds();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = httpClient;
        this.retryDelaysSeconds = normalizeRetryDelays(properties.getRetryDelaysSeconds());
        this.editEnabled = properties.isEditEnabled();
        String path = trimToNull(properties.getEditPath());
        this.editPath = path == null ? "/images/edits" : (path.startsWith("/") ? path : "/" + path);
        String editModelId = trimToNull(properties.getEditModel());
        this.editModel = editModelId == null ? modelId : editModelId;
    }

    @Override
    public GeneratedImage generate(ImageBrief brief) {
        Objects.requireNonNull(brief, "brief");
        if (shouldEdit(brief)) {
            return edit(brief);
        }
        return generateOnly(brief);
    }

    private boolean shouldEdit(ImageBrief brief) {
        return editEnabled && brief.hasReferenceImage();
    }

    private GeneratedImage generateOnly(ImageBrief brief) {
        String prompt = resolvePrompt(brief, false);
        String requestPrompt = ImagePromptComposer.apply(prompt);
        String model = resolveModel(brief, false);
        String size = toProviderSize(brief.size());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("prompt", requestPrompt);
        body.put("n", 1);
        // 强制 url：后续 S3 通过 HTTP 下载
        body.put("response_format", "url");
        if (size != null) {
            body.put("size", size);
        }

        String requestJson;
        try {
            requestJson = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new ImageProviderException("failed to serialize new2api request", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/images/generations"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "java-admin-image-provider/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        String responseBody = sendWithRetry(request, brief.imageIndex());
        String imageUrl = parseImageUrl(responseBody);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "new2api");
        metadata.put("model", model);
        metadata.put("requested_size", brief.size());
        metadata.put("provider_size", size);
        metadata.put("image_index", brief.imageIndex());
        metadata.put("response_format", "url");
        metadata.put("mode", "generate");

        return new GeneratedImage(
                brief.imageIndex(),
                imageUrl,
                "new2api://" + model + "/" + brief.imageIndex(),
                prompt,
                brief.size() == null ? size : brief.size(),
                model,
                metadata);
    }

    private GeneratedImage edit(ImageBrief brief) {
        String prompt = resolvePrompt(brief, true);
        String requestPrompt = ImagePromptComposer.apply(prompt);
        String model = resolveModel(brief, true);
        String size = toProviderSize(brief.size());
        DecodedReference ref = decodeReferenceDataUrl(brief.referenceImageDataUrl());

        String boundary =
                "----JavaAdminImageEditBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipartBody = buildEditMultipart(boundary, model, requestPrompt, size, ref);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + editPath))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "java-admin-image-provider/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        String responseBody = sendWithRetry(request, brief.imageIndex());
        String imageUrl = parseImageUrl(responseBody);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "new2api");
        metadata.put("model", model);
        metadata.put("base_model", defaultModel);
        metadata.put("requested_size", brief.size());
        metadata.put("provider_size", size);
        metadata.put("image_index", brief.imageIndex());
        metadata.put("response_format", "url");
        metadata.put("mode", "edit");
        metadata.put("edit_path", editPath);

        return new GeneratedImage(
                brief.imageIndex(),
                imageUrl,
                "new2api://" + model + "/" + brief.imageIndex(),
                prompt,
                brief.size() == null ? size : brief.size(),
                model,
                metadata);
    }

    private String resolvePrompt(ImageBrief brief, boolean forEdit) {
        if (forEdit) {
            String revision = trimToNull(brief.revisionInstruction());
            if (revision != null) {
                return revision;
            }
        }
        String prompt = brief.prompt() == null ? "" : brief.prompt().trim();
        if (prompt.isEmpty()) {
            throw new ImageProviderException("prompt is required");
        }
        return prompt;
    }

    private String resolveModel(ImageBrief brief, boolean forEdit) {
        String model = trimToNull(brief.model());
        if (model != null) {
            return model;
        }
        return forEdit ? editModel : defaultModel;
    }

    private static DecodedReference decodeReferenceDataUrl(String dataUrl) {
        String raw = trimToNull(dataUrl);
        if (raw == null || !raw.regionMatches(true, 0, "data:", 0, 5)) {
            throw new ImageProviderException("edit requires reference image data URL");
        }
        int comma = raw.indexOf(',');
        if (comma < 0) {
            throw new ImageProviderException("invalid reference data URL: missing comma");
        }
        String meta = raw.substring(0, comma);
        String payload = raw.substring(comma + 1);
        if (payload.isBlank()) {
            throw new ImageProviderException("invalid reference data URL: empty payload");
        }
        String mime = "image/png";
        int semi = meta.indexOf(';');
        int end = semi > 5 ? semi : comma;
        if (end > 5) {
            String m = meta.substring(5, end).trim();
            if (!m.isEmpty()) {
                mime = m;
            }
        }
        byte[] bytes;
        try {
            if (meta.toLowerCase(Locale.ROOT).contains(";base64")) {
                bytes = Base64.getDecoder().decode(payload);
            } else {
                bytes = java.net.URLDecoder.decode(payload, java.nio.charset.StandardCharsets.UTF_8)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            throw new ImageProviderException("invalid reference data URL base64: " + e.getMessage(), e);
        }
        if (bytes.length == 0) {
            throw new ImageProviderException("reference image is empty");
        }
        String ext = ImageContentTypes.extensionForMime(mime);
        String filename = "reference" + ext;
        return new DecodedReference(filename, bytes, mime);
    }

    private static byte[] buildEditMultipart(
            String boundary, String model, String prompt, String size, DecodedReference ref) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            writeFormField(out, boundary, "model", model);
            writeFormField(out, boundary, "prompt", prompt);
            writeFormField(out, boundary, "n", "1");
            writeFormField(out, boundary, "response_format", "url");
            if (size != null) {
                writeFormField(out, boundary, "size", size);
            }
            writeFileField(out, boundary, "image", ref.filename(), ref.mimeType(), ref.bytes());
            out.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageProviderException("failed to build edit multipart body", e);
        }
    }

    private static void writeFormField(java.io.ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(part.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeFileField(
            java.io.ByteArrayOutputStream out,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] data)
            throws IOException {
        String headers = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        out.write(headers.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record DecodedReference(String filename, byte[] bytes, String mimeType) {}

    /**
     * 按 {@link #retryDelaysSeconds} 发送请求：首次立即执行，失败后依次 sleep 再试。
     *
     * @param request HTTP 请求
     * @param imageIndex 图片序号（仅日志）
     * @return 成功响应 body
     */
    private String sendWithRetry(HttpRequest request, int imageIndex) {
        int maxAttempts = 1 + retryDelaysSeconds.size();
        ImageProviderException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return sendOnce(request);
            } catch (ImageProviderException ex) {
                lastError = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                int delaySeconds = retryDelaysSeconds.get(attempt - 1);
                log.warn(
                        "new2api image API failed (imageIndex={}, attempt={}/{}), retry in {}s: {}",
                        imageIndex,
                        attempt,
                        maxAttempts,
                        delaySeconds,
                        ex.getMessage());
                sleepSeconds(delaySeconds);
            }
        }
        throw lastError != null
                ? lastError
                : new ImageProviderException("new2api image API failed with no error detail");
    }

    private String sendOnce(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ImageProviderException("new2api image API request failed: " + describeThrowable(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageProviderException("new2api image API request interrupted", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ImageProviderException(
                    "new2api image API HTTP " + response.statusCode() + ": " + truncate(response.body(), 256));
        }
        return response.body();
    }

    private static void sleepSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageProviderException("new2api image API retry sleep interrupted", e);
        }
    }

    /**
     * 将业务侧宽高比转为 new2api / Seedream 请求用的 {@code 宽x高}。
     *
     * <p>已是像素尺寸（含 {@code x}/{@code X}）或无法识别的值原样返回。
     *
     * @param size brief 中的 size，可空
     * @return 请求 size；入参为空则 null
     */
    static String toProviderSize(String size) {
        String value = trimToNull(size);
        if (value == null) {
            return null;
        }
        if (value.toLowerCase(Locale.ROOT).matches("\\d+x\\d+")) {
            return value.toLowerCase(Locale.ROOT);
        }
        return switch (value) {
            case "1:1" -> "2048x2048";
            case "4:3" -> "2304x1728";
            case "3:4" -> "1728x2304";
            case "16:9" -> "2560x1440";
            case "9:16" -> "1440x2560";
            case "3:2" -> "2496x1664";
            case "2:3" -> "1664x2496";
            case "21:9" -> "3024x1296";
            case "4:5" -> "1664x2080";
            case "5:4" -> "2080x1664";
            default -> value;
        };
    }

    /**
     * 规范化重试秒数组：过滤 null / 负数，保留 0（表示立即再试）。
     */
    static List<Integer> normalizeRetryDelays(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(raw.size());
        for (Integer v : raw) {
            if (v == null || v < 0) {
                continue;
            }
            out.add(v);
        }
        return Collections.unmodifiableList(out);
    }

    /** 测试用：规范化后的重试秒数组。 */
    List<Integer> retryDelaysSeconds() {
        return retryDelaysSeconds;
    }

    /**
     * 解析 data[0].url；强制 url 模式。
     */
    private static String parseImageUrl(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ImageProviderException("new2api image API returned empty body");
        }
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new ImageProviderException("new2api image API returned no image data");
            }
            JsonNode first = data.get(0);
            String url = textOrNull(first.path("url"));
            if (url != null) {
                return url;
            }
            throw new ImageProviderException("new2api image API returned no url; body=" + truncate(responseBody, 200));
        } catch (ImageProviderException e) {
            throw e;
        } catch (IOException e) {
            throw new ImageProviderException("failed to parse new2api response", e);
        }
    }

    private static HttpClient defaultHttpClient(ImageProviderProperties.New2api properties) {
        int timeoutSeconds =
                properties == null || properties.getTimeoutSeconds() <= 0 ? 120 : properties.getTimeoutSeconds();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return trimToNull(text);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String stripTrailingSlash(String value) {
        String text = value.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

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
}
