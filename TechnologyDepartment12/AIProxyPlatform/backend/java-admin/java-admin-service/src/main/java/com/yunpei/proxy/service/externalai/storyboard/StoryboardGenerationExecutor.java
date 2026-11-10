package com.yunpei.proxy.service.externalai.storyboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.service.llm.LlmProvider;
import com.yunpei.proxy.service.llm.LlmProviderException;
import com.yunpei.proxy.service.llm.LlmRequest;
import com.yunpei.proxy.service.llm.LlmResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 分镜 V1 执行：组 context → 单次 LLM → 轻量 redact → 成功结果键。
 *
 * <p>对齐 Python {@code execute_video_storyboard} 精简路径；不做完整 image resolve / storyboard-v2。
 *
 * @author yunpei
 */
public final class StoryboardGenerationExecutor {

    public static final String PURPOSE = "storyboard";
    public static final String SCHEMA_VERSION = "external_storyboard.v1";

    /** 追加到分镜末尾，避免视频模型把 brief 口号排上画面。 */
    static final String NO_ONSCREEN_TEXT =
            "On-screen text: none. No titles, subtitles, captions, slogans, headlines, posters, UI, or watermarks.";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String PROMPT_RESOURCE = "external-ai/storyboard-system-prompt.txt";
    private static final AtomicReference<String> SYSTEM_PROMPT = new AtomicReference<>();

    private StoryboardGenerationExecutor() {}

    /**
     * 执行分镜文案生成。
     *
     * @param llm LLM 端口
     * @param payload 任务 payload
     * @param requestId 结果 request_id
     * @param creativeSeed 用于 redact 的种子（可空则按 requestId 生成）
     * @return {@code {request_id, storyboard_text, duration_seconds, aspect_ratio}}
     */
    public static Map<String, Object> execute(
            LlmProvider llm, Map<String, Object> payload, String requestId, String creativeSeed) {
        if (llm == null) {
            throw new LlmProviderException("llm provider is required");
        }
        Map<String, Object> body = payload == null ? Map.of() : payload;
        int duration = StoryboardContextBuilder.resolveDurationSeconds(body.get("duration_seconds"));
        String aspect = StoryboardContextBuilder.resolveAspectRatio(body.get("aspect_ratio"));
        List<String> imageUrls = StoryboardContextBuilder.normalizeImageUrls(body.get("image_urls"));
        String seed = clean(creativeSeed);
        if (seed == null) {
            seed = StoryboardContextBuilder.creativeSeed(requestId);
        }

        Map<String, Object> userContext = StoryboardContextBuilder.buildUserContext(body, null);
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("schema_version", SCHEMA_VERSION);
        userPayload.put("context", userContext);

        LlmResponse response = llm.complete(LlmRequest.of(systemPrompt(), writeJson(userPayload), PURPOSE));
        String storyboardText = extractStoryboardText(response == null ? null : response.content());
        storyboardText = ensureNoOnscreenText(
                StoryboardTextRedactor.redact(storyboardText, seed, imageUrls).strip());
        if (storyboardText.isEmpty()) {
            throw new LlmProviderException("LLM returned empty external storyboard text");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", requestId);
        result.put("storyboard_text", storyboardText);
        result.put("duration_seconds", duration);
        result.put("aspect_ratio", aspect);
        return result;
    }

    static String ensureNoOnscreenText(String storyboardText) {
        if (storyboardText == null || storyboardText.isBlank()) {
            return "";
        }
        String text = storyboardText.strip();
        if (text.toLowerCase(Locale.ROOT).contains("on-screen text: none")) {
            return text;
        }
        return text + " " + NO_ONSCREEN_TEXT;
    }

    static String extractStoryboardText(String content) {
        Map<String, Object> parsed = parseJson(content);
        if (parsed.containsKey("storyboard_text")) {
            String fromJson = clean(parsed.get("storyboard_text"));
            return fromJson == null ? "" : fromJson;
        }
        // 非 JSON 时容忍纯文本（调试）；已是 JSON 但缺字段则视为空
        if (!parsed.isEmpty()) {
            return "";
        }
        return content == null ? "" : content.strip();
    }

    static String systemPrompt() {
        String cached = SYSTEM_PROMPT.get();
        if (cached != null) {
            return cached;
        }
        try (InputStream in =
                StoryboardGenerationExecutor.class.getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) {
                throw new LlmProviderException("missing storyboard system prompt resource: " + PROMPT_RESOURCE);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                throw new LlmProviderException("storyboard system prompt resource is empty");
            }
            SYSTEM_PROMPT.compareAndSet(null, text);
            return SYSTEM_PROMPT.get();
        } catch (IOException ex) {
            throw new LlmProviderException("failed to load storyboard system prompt: " + ex.getMessage());
        }
    }

    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        try {
            Map<String, Object> map = MAPPER.readValue(trimmed, MAP_TYPE);
            return map == null ? Map.of() : map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new LlmProviderException("storyboard context serialization failed");
        }
    }

    private static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
