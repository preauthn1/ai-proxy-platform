package com.yunpei.proxy.service.externalai.topics;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 选题白名单 context 组装（对齐 Python {@code _build_external_topic_context}）。
 *
 * <p>剥离 brief/原始工单等私有字段，仅保留投放规划所需结构。
 *
 * @author yunpei
 */
public final class TopicContextBuilder {

    private static final int MAX_NAME = 255;
    private static final int MAX_WORK_ORDER_TYPE = 128;
    private static final int MAX_AUDIENCE_DESC = 500;

    private static final String[] FIELD_LABELS = {
        "项目名称",
        "产品名称",
        "品牌名称",
        "project name",
        "product name",
        "brand name",
        "投放国家",
        "投放时间",
        "日报时区",
        "投放媒体",
        "投放事件",
        "投放人群",
        "投放链接",
        "country",
        "media",
        "event",
        "audience",
        "link",
        "WhatsApp",
        "whatsapp",
        "Whats App",
        "联系方式",
        "电话",
        "phone",
        "tel"
    };

    private static final Pattern FIELD_LABEL_PATTERN = buildFieldLabelPattern();
    private static final Pattern URL_PATTERN = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);

    private TopicContextBuilder() {}

    /**
     * 从创建 payload 构建 LLM 用白名单 context。
     *
     * @param payload 任务 payload（snake_case 键）
     * @return 压缩后的 context map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> campaign = mapOrEmpty(body.get("campaign"));
        Map<String, Object> adset = mapOrEmpty(body.get("adset"));
        Map<String, Object> creative = mapOrEmpty(body.get("creative"));

        String canonicalName = extractProductName(body.get("product_name"));
        Object countryRaw = firstValue(
                body.get("country"),
                adset.get("countries"),
                adset.get("country_code"),
                campaign.get("countries"),
                campaign.get("country_code"),
                campaign.get("country"));
        Map<String, Object> targetLanguage = TargetLanguageContext.fromCountry(countryRaw);
        String countryCode = clean(targetLanguage.get("country_code"));
        if (countryCode == null) {
            countryCode = clean(countryRaw);
        }

        Map<String, Object> audience = new LinkedHashMap<>();
        putIfPresent(audience, "age_min", scalar(adset.get("age_min")));
        putIfPresent(audience, "age_max", scalar(adset.get("age_max")));
        putIfPresent(audience, "gender", gender(adset.get("gender")));
        putIfPresent(
                audience,
                "description",
                trim(firstText(adset.get("audience"), adset.get("audience_description")), MAX_AUDIENCE_DESC));

        Map<String, Object> delivery = new LinkedHashMap<>();
        putIfPresent(delivery, "optimization_goal", clean(adset.get("optimization_goal")));
        putIfPresent(delivery, "conversion_location", clean(adset.get("conversion_location")));
        putIfPresent(delivery, "destination_type", clean(adset.get("destination_type")));
        putIfPresent(
                delivery,
                "event_name",
                firstText(adset.get("event_name"), adset.get("customEventType"), adset.get("custom_event_type")));
        putIfPresent(
                delivery,
                "messaging_channels",
                textList(adset.get("messaging_channels"), adset.get("messagePlatform"), adset.get("message_platform")));
        putIfPresent(delivery, "app_store", clean(adset.get("mobile_app_store")));
        putIfPresent(delivery, "device_platforms", textList(adset.get("device_platforms")));
        putIfPresent(delivery, "cta", firstText(creative.get("btn_type"), creative.get("call_to_action")));

        String landingUrl = firstText(creative.get("link"), creative.get("landing_url"), adset.get("object_store_url"));
        String landingDomain = domainOf(landingUrl);

        Map<String, Object> identity = new LinkedHashMap<>();
        putIfPresent(identity, "canonical_name", canonicalName);

        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put(
                "work_order_type",
                trim(firstText(body.get("work_order_type"), body.get("industry"), "unspecified"), MAX_WORK_ORDER_TYPE));

        Map<String, Object> campaignOut = new LinkedHashMap<>();
        putIfPresent(campaignOut, "objective", clean(campaign.get("objective")));
        putIfPresent(campaignOut, "special_ad_categories", textList(campaign.get("special_ad_categories")));

        Map<String, Object> market = new LinkedHashMap<>();
        putIfPresent(market, "country_code", countryCode);
        Map<String, Object> lang = new LinkedHashMap<>();
        putIfPresent(lang, "label", targetLanguage.get("label"));
        putIfPresent(lang, "instruction", targetLanguage.get("instruction"));
        market.put("target_language", lang);

        Map<String, Object> landing = new LinkedHashMap<>();
        putIfPresent(landing, "domain", landingDomain);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("identity", identity);
        context.put("classification", classification);
        context.put("campaign", campaignOut);
        context.put("market", market);
        context.put("audience", audience);
        context.put("delivery", delivery);
        context.put("landing", landing);
        context.put("requested_count", resolveCount(body.get("count")));
        return compact(context);
    }

    /**
     * 解析 count：缺省/非法字符串 → 3；数值原样返回（越界由 Service {@code validatePayload} 拒识）。
     */
    public static int resolveCount(Object raw) {
        if (raw == null) {
            return 3;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return 3;
        }
    }

    /**
     * 从工单式 product_name 提取可展示品牌/产品名。
     *
     * <p>剥离「投放国家：…」「Country: …」「WhatsApp：…」等字段标签及后续元数据；
     * 选题与文案共用，避免把 Facebook 投放配置写进用户可见内容。
     *
     * @param value 原始 product_name
     * @return 清洗后的名称；无法解析时回退原串（截断到上限）
     */
    public static String extractProductName(Object value) {
        String original = clean(value);
        if (original == null) {
            return null;
        }
        String text = original.split("\\R", 2)[0].strip();
        Matcher label = FIELD_LABEL_PATTERN.matcher(text);
        if (label.find()) {
            text = text.substring(0, label.start())
                    .replaceAll("[,，;；\\s]+$", "")
                    .strip();
        }
        Matcher url = URL_PATTERN.matcher(text);
        if (url.find()) {
            text = text.substring(0, url.start()).replaceAll("[,，;；\\s]+$", "").strip();
        }
        if (text == null || text.isBlank()) {
            text = original;
        }
        return trim(text, MAX_NAME);
    }

    private static Pattern buildFieldLabelPattern() {
        StringBuilder sb = new StringBuilder("(?:");
        List<String> sorted = new ArrayList<>(List.of(FIELD_LABELS));
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(Pattern.quote(sorted.get(i)));
        }
        sb.append(")\\s*[:：]");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
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

    private static Object firstValue(Object... values) {
        for (Object value : values) {
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (clean(item) != null) {
                        return item;
                    }
                }
                continue;
            }
            if (clean(value) != null) {
                return value;
            }
        }
        return null;
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

    private static Object scalar(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        if (value instanceof Number) {
            return value;
        }
        return clean(value);
    }

    private static String gender(Object value) {
        String gender = clean(value);
        if (gender == null) {
            return null;
        }
        String lower = gender.toLowerCase(Locale.ROOT);
        if (Set.of("all", "any", "unrestricted", "不限", "全部").contains(lower) || "不限".equals(gender)) {
            return null;
        }
        return gender;
    }

    private static List<String> textList(Object... values) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            Iterable<?> items = value instanceof List<?> list ? list : List.of(value);
            for (Object item : items) {
                String text = clean(item);
                if (text == null) {
                    continue;
                }
                String key = text.toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private static String domainOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compact(Map<String, Object> value) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : value.entrySet()) {
            Object compacted = compactValue(e.getValue());
            if (!isEmpty(compacted)) {
                out.put(e.getKey(), compacted);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object compactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object compacted = compactValue(e.getValue());
                if (!isEmpty(compacted) && e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), compacted);
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                Object compacted = compactValue(item);
                if (!isEmpty(compacted)) {
                    out.add(compacted);
                }
            }
            return out;
        }
        return value;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (!isEmpty(value)) {
            map.put(key, value);
        }
    }

    private static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
