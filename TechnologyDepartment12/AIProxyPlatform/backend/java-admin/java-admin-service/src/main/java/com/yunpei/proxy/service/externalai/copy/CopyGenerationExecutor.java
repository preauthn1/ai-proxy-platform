package com.yunpei.proxy.service.externalai.copy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.service.externalai.workorder.CustomEventTypes;
import com.yunpei.proxy.service.llm.LlmProvider;
import com.yunpei.proxy.service.llm.LlmProviderException;
import com.yunpei.proxy.service.llm.LlmRequest;
import com.yunpei.proxy.service.llm.LlmResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文案生成执行：组 context → 单次 LLM batch → 基础非空/长度上限校验 → 展平为 copywritings。
 *
 * <p>无独立选题接口；system prompt 与 context 内化创意角度优化。对齐成功结果键
 * {@code request_id}/{@code copywritings}；不做多轮 repair 与 safe_fallback。
 *
 * @author yunpei
 */
public final class CopyGenerationExecutor {

    public static final String PURPOSE = "copy";
    public static final String SCHEMA_VERSION = "external_copy.v4";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String PROMPT_RESOURCE = "external-ai/copy-batch-system-prompt.txt";
    private static final AtomicReference<String> SYSTEM_PROMPT = new AtomicReference<>();

    private CopyGenerationExecutor() {}

    /**
     * 执行文案生成。
     *
     * @param llm LLM 端口
     * @param payload 任务 payload
     * @param requestId 结果 request_id
     * @return {@code {request_id, copywritings: [...]}}
     */
    public static Map<String, Object> execute(LlmProvider llm, Map<String, Object> payload, String requestId) {
        if (llm == null) {
            throw new LlmProviderException("llm provider is required");
        }
        Map<String, Object> body = payload == null ? Map.of() : payload;
        int count = CopyContextBuilder.resolveCount(body.get("count"));
        if (count < CopyContextBuilder.MIN_COUNT) {
            count = CopyContextBuilder.MIN_COUNT;
        }
        if (count > CopyContextBuilder.MAX_COUNT) {
            count = CopyContextBuilder.MAX_COUNT;
        }

        Map<String, Object> context = CopyContextBuilder.build(body);
        Map<String, Integer> lengthLimits = CopyContextBuilder.lengthLimits(body);
        Map<String, Object> market = mapOrEmpty(context.get("market"));
        Map<String, Object> targetLanguage = mapOrEmpty(market.get("target_language"));
        Map<String, Map<String, Integer>> preferred =
                CopyContextBuilder.preferredLengthRanges(lengthLimits, targetLanguage);

        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("schema_version", SCHEMA_VERSION);
        userPayload.put("context", context);
        userPayload.put("requested_count", count);
        userPayload.put("length_limits", lengthLimits);
        userPayload.put("preferred_length_ranges", preferred);
        userPayload.put("existing_variants", List.of());
        userPayload.put("repair_feedback", List.of());

        LlmResponse response = llm.complete(LlmRequest.of(systemPrompt(), writeJson(userPayload), PURPOSE));
        List<Map<String, Object>> variants =
                acceptVariants(response == null ? null : response.content(), lengthLimits, count, targetLanguage);
        if (variants.isEmpty()) {
            throw new LlmProviderException("LLM returned no usable external copy variants");
        }

        String cta = resolveCta(body);
        String callToAction = normalizeCta(cta);
        String eventType = resolveEventType(body);

        List<Map<String, Object>> copywritings = new ArrayList<>();
        for (Map<String, Object> variant : variants) {
            copywritings.add(toCopywriting(variant, cta, callToAction, eventType));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", requestId);
        result.put("copywritings", copywritings);
        return result;
    }

    static List<Map<String, Object>> acceptVariants(String content, Map<String, Integer> lengthLimits, int limit) {
        return acceptVariants(content, lengthLimits, limit, Map.of());
    }

    /**
     * 解析并校验 variants；非中文市场拒绝用户可见字段中的汉字（避免英文广告突兀夹杂中文）。
     */
    static List<Map<String, Object>> acceptVariants(
            String content, Map<String, Integer> lengthLimits, int limit, Map<String, Object> targetLanguage) {
        Map<String, Object> parsed = parseJson(content);
        Object raw = parsed.get("variants");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        boolean forbidCjk = !CopyContextBuilder.isChineseTargetMarket(targetLanguage);
        List<Map<String, Object>> accepted = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> variant = normalizeVariant(map, lengthLimits, forbidCjk);
            if (variant == null) {
                continue;
            }
            accepted.add(variant);
            if (accepted.size() >= limit) {
                break;
            }
        }
        return accepted;
    }

    private static Map<String, Object> normalizeVariant(
            Map<?, ?> data, Map<String, Integer> lengthLimits, boolean forbidCjk) {
        String primary = firstText(data.get("primary_text"), data.get("body"));
        String headline = clean(data.get("headline"));
        String description = clean(data.get("description"));
        if (primary == null || headline == null || description == null) {
            return null;
        }
        if (exceeds(primary, lengthLimits.get("primary_text"))
                || exceeds(headline, lengthLimits.get("headline"))
                || exceeds(description, lengthLimits.get("description"))) {
            return null;
        }
        if (forbidCjk
                && (CopyContextBuilder.containsCjk(primary)
                        || CopyContextBuilder.containsCjk(headline)
                        || CopyContextBuilder.containsCjk(description))) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("primary_text", primary);
        out.put("headline", headline);
        out.put("description", description);
        return out;
    }

    private static boolean exceeds(String text, Integer max) {
        return max != null && text.length() > max;
    }

    private static Map<String, Object> toCopywriting(
            Map<String, Object> variant, String cta, String callToAction, String eventType) {
        String primary = String.valueOf(variant.get("primary_text"));
        String headline = String.valueOf(variant.get("headline"));
        String description = String.valueOf(variant.get("description"));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("primary_text", primary);
        nested.put("headline", headline);
        nested.put("description", description);
        nested.put("cta", cta);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("primary_text", primary);
        item.put("headline", headline);
        item.put("description", description);
        item.put("cta", cta);
        item.put("call_to_action", callToAction);
        item.put("customEventType", eventType);
        item.put("copywriting", nested);
        return item;
    }

    static String resolveCta(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> constraints = mapOrEmpty(body.get("constraints"));
        Map<String, Object> creative = mapOrEmpty(body.get("creative"));
        String cta = firstText(
                constraints.get("cta"),
                constraints.get("call_to_action"),
                creative.get("btn_type"),
                creative.get("call_to_action"));
        return cta == null ? "LEARN_MORE" : cta;
    }

    static String normalizeCta(String value) {
        if (value == null || value.isBlank()) {
            return "LEARN_MORE";
        }
        String normalized =
                value.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        String compact = normalized.replace("_", "");
        return switch (compact) {
            case "LEARNMORE" -> "LEARN_MORE";
            case "SHOPNOW" -> "SHOP_NOW";
            case "SIGNUP" -> "SIGN_UP";
            case "CONTACTUS" -> "CONTACT_US";
            case "APPLYNOW" -> "APPLY_NOW";
            default -> normalized;
        };
    }

    static String resolveEventType(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String direct = firstText(body.get("customEventType"), body.get("custom_event_type"));
        if (direct != null) {
            return direct;
        }
        return CustomEventTypes.customEventType(body.get("event_name"));
    }

    static String systemPrompt() {
        String cached = SYSTEM_PROMPT.get();
        if (cached != null) {
            return cached;
        }
        try (InputStream in = CopyGenerationExecutor.class.getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) {
                throw new LlmProviderException("missing copy system prompt resource: " + PROMPT_RESOURCE);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                throw new LlmProviderException("copy system prompt resource is empty");
            }
            SYSTEM_PROMPT.compareAndSet(null, text);
            return SYSTEM_PROMPT.get();
        } catch (IOException ex) {
            throw new LlmProviderException("failed to load copy system prompt: " + ex.getMessage());
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
            throw new LlmProviderException("copy context serialization failed");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = clean(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
