package com.yunpei.proxy.service.externalad;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 广告分析 payload 规范化与 SHA-256（对齐 Python {@code canonicalize_ad_analysis_payload}）。
 *
 * @author yunpei
 */
public final class AdAnalysisPayloadCanonicalizer {

    static final Set<String> NUMERIC_KEYS = Set.of(
            "spend",
            "impressions",
            "reach",
            "frequency",
            "clicks",
            "inline_link_clicks",
            "ctr",
            "inline_link_click_ctr",
            "cpc",
            "cpm",
            "value",
            "daily_budget",
            "lifetime_budget");
    static final Set<String> IGNORED_CREATIVE_KEYS = Set.of("thumbnail_url", "video_keyframes");

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private AdAnalysisPayloadCanonicalizer() {}

    /**
     * 去掉 {@code external_request_id} 与服务端媒体字段后规范化。
     */
    public static Map<String, Object> canonicalize(Map<String, Object> payload) {
        Map<String, Object> raw = excludeNone(payload);
        raw.remove("external_request_id");
        Object creative = raw.get("creative");
        if (creative instanceof Map<?, ?> creativeMap) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : creativeMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!IGNORED_CREATIVE_KEYS.contains(key)) {
                    cleaned.put(key, entry.getValue());
                }
            }
            raw.put("creative", cleaned);
        }
        Object normalized = normalizeValue(raw, null);
        if (!(normalized instanceof Map<?, ?> map)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "normalized ad-analysis payload must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    /** SHA-256 hex of canonical JSON（sort_keys、紧凑分隔符、不转义非 ASCII）。 */
    public static String payloadHash(Map<String, Object> payload) {
        Map<String, Object> canonical = canonicalize(payload);
        try {
            String json = MAPPER.writeValueAsString(toSorted(canonical));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw BizException.of(ResultCode.PARAM_INVALID, "payload hash failed");
        }
    }

    static Map<String, Object> excludeNone(Map<String, Object> payload) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (payload == null) {
            return raw;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                raw.put(entry.getKey(), entry.getValue());
            }
        }
        return raw;
    }

    private static Object normalizeValue(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                String childKey = String.valueOf(entry.getKey());
                result.put(childKey, normalizeValue(entry.getValue(), childKey));
            }
            Object actions = result.get("actions");
            if (actions instanceof List<?> items) {
                List<Object> sorted = new ArrayList<>(items);
                sorted.sort(Comparator.comparing((Object item) -> actionPart(item, "action_type"))
                        .thenComparing(item -> actionPart(item, "value")));
                result.put("actions", sorted);
            }
            Object siblings = result.get("siblings");
            if (siblings instanceof List<?> items) {
                List<Object> sorted = new ArrayList<>(items);
                sorted.sort(Comparator.comparing(AdAnalysisPayloadCanonicalizer::siblingSortKey)
                        .thenComparing(AdAnalysisPayloadCanonicalizer::siblingFallback));
                result.put("siblings", sorted);
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeValue(item, null));
            }
            return normalized;
        }
        if (key != null && NUMERIC_KEYS.contains(key) && isNumericLike(value)) {
            try {
                BigDecimal decimal =
                        new BigDecimal(String.valueOf(value).replace(",", "").strip());
                return decimal.stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private static boolean isNumericLike(Object value) {
        return value instanceof Number || value instanceof String;
    }

    private static String actionPart(Object item, String field) {
        if (item instanceof Map<?, ?> map && map.get(field) != null) {
            return String.valueOf(map.get(field));
        }
        return "";
    }

    private static String siblingSortKey(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return "";
        }
        Object identity = map.get("facebook_ad_id");
        if (identity == null && map.get("creative") instanceof Map<?, ?> creative) {
            identity = creative.get("fb_id");
            if (identity == null) {
                identity = creative.get("facebook_ad_id");
            }
        }
        return identity == null ? "" : String.valueOf(identity);
    }

    private static String siblingFallback(Object value) {
        try {
            return MAPPER.writeValueAsString(toSorted(value));
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private static Object toSorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), toSorted(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            for (Object item : list) {
                copied.add(toSorted(item));
            }
            return copied;
        }
        return value;
    }
}
