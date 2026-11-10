package com.yunpei.proxy.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI 兼容 chat/completions 薄实现。
 *
 * <p>本票以接口齐套为主；生产切换 {@code llm.provider=openai-compatible} 后生效。
 *
 * @author yunpei
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** chat/completions 单次请求上限；超时后抛 {@link LlmTimeoutException} 供 workflow 重试。 */
    static final int REQUEST_TIMEOUT_SECONDS = 120;

    private static final int CONNECT_TIMEOUT_SECONDS = 30;

    private final LlmProviderProperties.OpenAiCompatible config;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmProvider(LlmProviderProperties.OpenAiCompatible config) {
        this.config = config == null ? new LlmProviderProperties.OpenAiCompatible() : config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (request == null
                || request.userPrompt() == null
                || request.userPrompt().isBlank()) {
            throw new LlmProviderException("userPrompt is required");
        }
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmProviderException("llm.openai-compatible.api-key is required");
        }
        String baseUrl = trimTrailingSlash(config.getBaseUrl());
        if (baseUrl == null) {
            throw new LlmProviderException("llm.openai-compatible.base-url is required");
        }
        String model = config.getModel() == null || config.getModel().isBlank() ? "gpt-4o-mini" : config.getModel();
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                ObjectNode system = messages.addObject();
                system.put("role", "system");
                system.put("content", request.systemPrompt());
            }
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", request.userPrompt());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = sendWithHardTimeout(httpRequest);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmProviderException(
                        "openai-compatible HTTP " + response.statusCode() + ": " + truncate(response.body(), 256));
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new LlmProviderException("openai-compatible response missing choices[0].message.content");
            }
            String usedModel = root.path("model").asText(model);
            return LlmResponse.of(contentNode.asText(), usedModel);
        } catch (LlmProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw wrapCallFailure(ex);
        }
    }

    /**
     * {@link HttpRequest#timeout} 只覆盖到响应头到达；慢正文仍可能挂很久。
     * {@code orTimeout} 对整段 future 做 2 分钟硬截止。
     */
    private HttpResponse<String> sendWithHardTimeout(HttpRequest httpRequest) {
        CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        try {
            return future.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        } catch (CompletionException | CancellationException ex) {
            future.cancel(true);
            throw wrapCallFailure(ex);
        }
    }

    private static RuntimeException wrapCallFailure(Throwable ex) {
        Throwable cause = unwrap(ex);
        if (cause instanceof LlmProviderException llmEx) {
            return llmEx;
        }
        if (isTimeout(cause)) {
            return new LlmTimeoutException(
                    "openai-compatible call timed out after " + REQUEST_TIMEOUT_SECONDS + "s", cause);
        }
        return new LlmProviderException(
                "openai-compatible call failed: "
                        + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()),
                cause);
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? ex : current;
    }

    private static boolean isTimeout(Throwable ex) {
        return ex instanceof TimeoutException || ex instanceof HttpTimeoutException;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
