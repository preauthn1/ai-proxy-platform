package com.yunpei.proxy.service.externalai.topics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.service.llm.LlmProvider;
import com.yunpei.proxy.service.llm.LlmProviderException;
import com.yunpei.proxy.service.llm.LlmRequest;
import com.yunpei.proxy.service.llm.LlmResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 选题生成执行：组 context → LLM → 规范化 topics 结果。
 *
 * <p>对齐 Python {@code execute_topic_selection} 的精简路径（单次 LLM 调用 + 结构规范化）。
 *
 * @author yunpei
 */
public final class TopicSelectionExecutor {

    public static final String PURPOSE = "topics";
    public static final String SYSTEM_PROMPT =
            "You are a senior Facebook/Meta advertising topic strategist serving an external "
                    + "integration API. Return valid JSON only with root key 'topics'. Use only the "
                    + "whitelisted context in the user payload; absent fields are unknown and must not "
                    + "cause failure. Generate the requested number of distinct, executable Facebook ad "
                    + "topic directions. Every topic must include non-empty title, angle, audience, "
                    + "selling_points, and rationale. selling_points must be a non-empty list. angle_type, "
                    + "risk_notes, and score are optional.\n\n"
                    + "Interpret classification.work_order_type semantically as mandatory business context. "
                    + "topic.title is user-facing and must follow market.target_language.instruction. "
                    + "angle, audience, selling_points, risk_notes, and rationale are operator-facing "
                    + "fields and must be written in Simplified Chinese. Avoid duplicates listed in "
                    + "existing_topics.";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String DEFAULT_ANGLE = "围绕产品核心价值设计清晰、可执行的广告沟通方向。";
    private static final String DEFAULT_AUDIENCE = "面向目标市场的潜在兴趣用户，结合现有投放设置持续测试与优化。";
    private static final String DEFAULT_SELLING_POINT = "围绕产品核心价值建立清晰、可测试的广告沟通。";
    private static final String DEFAULT_RATIONALE = "将产品价值与当前投放目标结合，便于在 Facebook 广告中测试用户反馈。";

    private TopicSelectionExecutor() {}

    /**
     * 执行选题生成。
     *
     * @param llm LLM 端口
     * @param payload 任务 payload
     * @param requestId 结果 request_id
     * @return {@code {request_id, topics: [...]}}
     */
    public static Map<String, Object> execute(LlmProvider llm, Map<String, Object> payload, String requestId) {
        if (llm == null) {
            throw new LlmProviderException("llm provider is required");
        }
        Map<String, Object> body = payload == null ? Map.of() : payload;
        int limit = TopicContextBuilder.resolveCount(body.get("count"));
        if (limit < 1) {
            limit = 1;
        }
        if (limit > 5) {
            limit = 5;
        }
        Map<String, Object> context = TopicContextBuilder.build(body);
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("schema_version", "external_topics.v2");
        userPayload.put("context", context);
        userPayload.put("limit", limit);
        userPayload.put("existing_topics", List.of());

        LlmResponse response = llm.complete(LlmRequest.of(SYSTEM_PROMPT, writeJson(userPayload), PURPOSE));
        List<Map<String, Object>> topics =
                normalizeTopics(response == null ? null : response.content(), context, limit);
        if (topics.isEmpty()) {
            throw new LlmProviderException("LLM returned no usable external topics");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", requestId);
        result.put("topics", topics);
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> normalizeTopics(String content, Map<String, Object> context, int limit) {
        Object rawTopics = extractTopicsNode(content);
        if (!(rawTopics instanceof List<?> list)) {
            return List.of();
        }
        int cap = limit < 1 ? 1 : Math.min(limit, 5);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> topic = normalizeOne(map, context);
            if (topic == null) {
                continue;
            }
            String key = String.valueOf(topic.get("title")).toLowerCase(Locale.ROOT)
                    + '\0'
                    + String.valueOf(topic.get("angle")).toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            out.add(topic);
            if (out.size() >= cap) {
                break;
            }
        }
        return out;
    }

    /**
     * 从 LLM content 取出 topics 节点：支持 {@code {"topics":[...]}}，以及根即为数组的容错形态。
     */
    static Object extractTopicsNode(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = stripMarkdownFence(content.trim());
        try {
            // 根为 topics 数组
            if (trimmed.startsWith("[")) {
                return MAPPER.readValue(trimmed, List.class);
            }
            Map<String, Object> map = MAPPER.readValue(trimmed, MAP_TYPE);
            if (map == null) {
                return null;
            }
            Object topics = map.get("topics");
            if (topics != null) {
                return topics;
            }
            // 偶发包一层 data/result
            for (String key : List.of("data", "result", "payload")) {
                Object nested = map.get(key);
                if (nested instanceof Map<?, ?> nestedMap && nestedMap.get("topics") != null) {
                    return nestedMap.get("topics");
                }
                if (nested instanceof List<?>) {
                    return nested;
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, Object> normalizeOne(Map<?, ?> data, Map<String, Object> context) {
        String rawTitle = clean(data.get("title"));
        String rawAngle = clean(data.get("angle"));
        String title = rawTitle != null ? rawTitle : rawAngle;
        String angle = rawAngle != null ? rawAngle : rawTitle;
        if (title == null || angle == null) {
            return null;
        }
        // 非中文市场丢弃「纯中文」title；允许「中文品名 + 目标语 copy」（如 多肽5: Discover…）
        if (!titleUsesTargetLanguage(title, context)) {
            return null;
        }

        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("title", title);
        topic.put("angle", operatorText(angle, DEFAULT_ANGLE));
        topic.put("angle_type", clean(data.get("angle_type")));
        topic.put("audience", operatorText(data.get("audience"), DEFAULT_AUDIENCE));
        topic.put("selling_points", sellingPoints(data.get("selling_points")));
        topic.put("rationale", operatorText(data.get("rationale"), DEFAULT_RATIONALE));
        return topic;
    }

    private static List<String> sellingPoints(Object raw) {
        List<String> points = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String text = operatorText(item, null);
                if (text != null) {
                    points.add(text);
                }
            }
        } else {
            String single = operatorText(raw, null);
            if (single != null) {
                points.add(single);
            }
        }
        if (points.isEmpty()) {
            points.add(DEFAULT_SELLING_POINT);
        }
        return points;
    }

    private static String operatorText(Object value, String fallback) {
        String text = clean(value);
        if (text != null && containsChinese(text)) {
            return text;
        }
        return fallback;
    }

    /**
     * 判断 title 是否可用于当前目标市场。
     *
     * <p>中文市场：一律接受。非中文市场：拒绝纯中文 title；若含中文品名但同时有足够的
     * 非 CJK 可展示文案（常见 {@code 品名: English hook}），则接受，避免 mock/LLM 把
     * product_name 拼进 title 后被整批丢弃。
     */
    static boolean titleUsesTargetLanguage(String title, Map<String, Object> context) {
        if (title == null || title.isBlank()) {
            return false;
        }
        if (isChineseTargetMarket(context)) {
            return true;
        }
        if (!containsChinese(title)) {
            return true;
        }
        // 混合 title：要求至少有一段可作投放展示的非 CJK 字母/数字文案
        return hasNonCjkMarketingCopy(title);
    }

    private static boolean isChineseTargetMarket(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        Object market = context.get("market");
        if (!(market instanceof Map<?, ?> marketMap)) {
            return false;
        }
        Object target = marketMap.get("target_language");
        if (!(target instanceof Map<?, ?> lang)) {
            return false;
        }
        String label = clean(lang.get("label"));
        String instruction = clean(lang.get("instruction"));
        String labelLower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        String instructionLower = instruction == null ? "" : instruction.toLowerCase(Locale.ROOT);
        return labelLower.equals("chinese") || instructionLower.startsWith("use chinese");
    }

    private static boolean containsChinese(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    /** 是否含可展示的非 CJK 字母/数字片段（忽略纯标点与空白）。 */
    private static boolean hasNonCjkMarketingCopy(String value) {
        int run = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || isSeparator(cp)) {
                if (run >= 2) {
                    return true;
                }
                run = 0;
                continue;
            }
            if (isCjkCodePoint(cp)) {
                if (run >= 2) {
                    return true;
                }
                run = 0;
                continue;
            }
            if (Character.isLetterOrDigit(cp)) {
                run++;
                if (run >= 2) {
                    return true;
                }
            }
        }
        return run >= 2;
    }

    private static boolean isSeparator(int cp) {
        return cp == ':' || cp == '：' || cp == '-' || cp == '—' || cp == '/' || cp == '|' || cp == '·' || cp == '.';
    }

    private static boolean isCjkCodePoint(int cp) {
        return (cp >= 0x4e00 && cp <= 0x9fff)
                || (cp >= 0x3400 && cp <= 0x4dbf)
                || (cp >= 0xf900 && cp <= 0xfaff)
                || (cp >= 0x3000 && cp <= 0x303f);
    }

    /** 容忍模型偶发 markdown fence（```json ... ```）。 */
    private static String stripMarkdownFence(String text) {
        if (text == null || !text.startsWith("```")) {
            return text;
        }
        int firstNl = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstNl > 0 && lastFence > firstNl) {
            return text.substring(firstNl + 1, lastFence).trim();
        }
        return text;
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new LlmProviderException("topic context serialization failed");
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
