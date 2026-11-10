package com.yunpei.proxy.service.externalai.copy;

import com.yunpei.proxy.service.externalai.topics.TargetLanguageContext;
import com.yunpei.proxy.service.externalai.topics.TopicContextBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文案白名单 context 与长度上限（对齐 Python {@code _build_external_copy_context} /
 * {@code _external_copy_length_limits} 的精简路径）。
 *
 * @author yunpei
 */
public final class CopyContextBuilder {

    public static final int DEFAULT_PRIMARY_TEXT_MAX = 500;
    public static final int DEFAULT_HEADLINE_MAX = 40;
    public static final int DEFAULT_DESCRIPTION_MAX = 80;
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 5;

    private static final int MAX_AUDIENCE = 1000;
    private static final int MAX_BRIEF = 2000;
    private static final int MAX_SELLING_POINT = 300;

    /**
     * 运营/素材备注：不应作为消费者卖点喂给文案模型。
     *
     * <p>匹配「画面建议」「镜头」「视频脚本」「投放媒体」等前缀或关键词。
     */
    private static final Pattern OPERATOR_NOTE_POINT =
            Pattern.compile("(?i)^(画面|镜头|分镜|视频|素材|投放媒体|投放时间|billing|bid[_\\s]?strategy|optimization)"
                    + "|(画面建议|镜头语言|视频脚本|素材建议|creative[_\\s]?direction|visual[_\\s]?direction)");

    /**
     * 常见中文品名词 → 英文展示名（最长优先替换）。非中文市场避免把汉字直接塞进广告。
     */
    private static final String[][] CJK_PRODUCT_GLOSSARY = {
        {"益生菌", "Probiotic"},
        {"胶原蛋白", "Collagen"},
        {"蛋白粉", "Protein"},
        {"维生素", "Vitamin"},
        {"保健品", "Supplement"},
        {"美妆", "Beauty"},
        {"多肽", "Peptide"},
        {"蛋白", "Protein"},
        {"瘦身", "Slim"},
        {"减脂", "Fat Burn"},
        {"健康", "Health"},
    };

    private CopyContextBuilder() {}

    /**
     * 从创建 payload 构建 LLM 用白名单 context。
     *
     * @param payload 任务 payload（snake_case / camelCase 混用键）
     * @return 压缩后的 context
     */
    public static Map<String, Object> build(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> campaign = mapOrEmpty(body.get("campaign"));
        Map<String, Object> adset = mapOrEmpty(body.get("adset"));

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

        // 与选题一致：剥离工单字段，再按目标市场本地化展示名（英文广告禁用突兀中文品名）
        String sourceProductName = TopicContextBuilder.extractProductName(body.get("product_name"));
        String productName = localizeProductNameForCopy(sourceProductName, targetLanguage);
        String campaignName = clean(campaign.get("name"));
        if (campaignName != null && productName != null && campaignName.equalsIgnoreCase(productName)) {
            campaignName = null;
        }

        Map<String, Object> market = new LinkedHashMap<>();
        putIfPresent(market, "country_code", countryCode);
        Map<String, Object> lang = new LinkedHashMap<>();
        putIfPresent(lang, "label", targetLanguage.get("label"));
        putIfPresent(lang, "instruction", targetLanguage.get("instruction"));
        market.put("target_language", lang);

        // 仅保留受众描述；age/gender 是定向参数，不属于用户可见文案事实
        Map<String, Object> audience = new LinkedHashMap<>();
        putIfPresent(
                audience,
                "description",
                trim(
                        firstText(body.get("audience"), adset.get("audience"), adset.get("audience_description")),
                        MAX_AUDIENCE));

        Map<String, Object> brand = new LinkedHashMap<>();
        putIfPresent(brand, "product_name", productName);
        putIfPresent(brand, "campaign_name", campaignName);
        brand.put(
                "product_name_instruction",
                "product_name is already the user-facing brand/product form for the target market "
                        + "(campaign metadata stripped; source-language characters localized when the market "
                        + "is not Chinese). Use this form as-is in primary_text/headline—do not reintroduce "
                        + "Chinese characters, pinyin leftovers that look unfinished, delivery settings, phone "
                        + "numbers, ages, media channels, or schedule. Never invent a different brand name.");

        Map<String, Object> copyDirection = new LinkedHashMap<>();
        // brief 为可选运营备注；不再依赖 /topics 选题结果。缺 brief 时模型从 brand/audience/selling_points 自建角度。
        String creativeBrief = trim(clean(body.get("brief")), MAX_BRIEF);
        putIfPresent(copyDirection, "creative_brief", creativeBrief);
        // 兼容旧 mock/调用方仍读 selected_topic
        putIfPresent(copyDirection, "selected_topic", creativeBrief);
        putIfPresent(copyDirection, "selling_points", consumerSellingPoints(body.get("selling_points")));
        copyDirection.put(
                "strategy_instruction",
                creativeBrief == null
                        ? "No operator brief was supplied (topics pipeline removed). Invent 1 distinct paid-social "
                                + "angle per variant from brand.product_name, audience, and selling_points only. "
                                + "Prefer concrete consumer benefits and a soft next step; do not invent phone "
                                + "numbers, ages, or delivery/ops metadata."
                        : "creative_brief is free-form operator input (not a pre-selected topic title). Extract "
                                + "the consumer-facing idea and rewrite it into natural target-language ad copy; "
                                + "do not paste operator Chinese or internal notes into user-facing fields.");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("market", market);
        context.put("audience", audience);
        context.put("brand", brand);
        context.put("copy_direction", copyDirection);
        return compact(context);
    }

    /**
     * 非中文市场：把含汉字的 product_name 转成可投放的拉丁展示名；中文市场原样返回。
     *
     * <p>例：{@code 多肽5} + US/English → {@code Peptide 5}。
     */
    public static String localizeProductNameForCopy(String productName, Map<String, Object> targetLanguage) {
        if (productName == null || productName.isBlank()) {
            return productName;
        }
        if (isChineseTargetMarket(targetLanguage) || !containsCjk(productName)) {
            return productName.strip();
        }
        String text = productName.strip();
        for (String[] pair : CJK_PRODUCT_GLOSSARY) {
            if (text.contains(pair[0])) {
                text = text.replace(pair[0], " " + pair[1] + " ");
            }
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjkCodePoint(cp)) {
                kept.append(' ');
                continue;
            }
            kept.appendCodePoint(cp);
        }
        text = kept.toString()
                .replaceAll("\\s+", " ")
                .strip()
                // Peptide5 → Peptide 5
                .replaceAll("(?<=\\p{L})(?=\\d)", " ")
                .replaceAll("(?<=\\d)(?=\\p{L})", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (text.isBlank()) {
            return "this offer";
        }
        return text;
    }

    /** 目标市场是否以中文为用户可见语言。 */
    public static boolean isChineseTargetMarket(Map<String, Object> targetLanguage) {
        if (targetLanguage == null) {
            return false;
        }
        String label = clean(targetLanguage.get("label"));
        String instruction = clean(targetLanguage.get("instruction"));
        String labelLower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        String instructionLower = instruction == null ? "" : instruction.toLowerCase(Locale.ROOT);
        return labelLower.contains("chinese") || instructionLower.startsWith("use chinese");
    }

    public static boolean containsCjk(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if (isCjkCodePoint(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isCjkCodePoint(int cp) {
        return (cp >= 0x4e00 && cp <= 0x9fff)
                || (cp >= 0x3400 && cp <= 0x4dbf)
                || (cp >= 0xf900 && cp <= 0xfaff)
                || (cp >= 0x3000 && cp <= 0x303f);
    }

    /**
     * 解析 count：缺省/非法 → 1；数值原样返回（越界由 Service 拒识）。
     */
    public static int resolveCount(Object raw) {
        if (raw == null) {
            return DEFAULT_COUNT;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_COUNT;
        }
    }

    /**
     * 主字段长度上限（constraints 可覆盖；非法/非正 → 默认；正数下限 10）。
     */
    public static Map<String, Integer> lengthLimits(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> constraints = mapOrEmpty(body.get("constraints"));
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("primary_text", positiveInt(constraints.get("primary_text_max_length"), DEFAULT_PRIMARY_TEXT_MAX));
        limits.put("headline", positiveInt(constraints.get("headline_max_length"), DEFAULT_HEADLINE_MAX));
        limits.put("description", positiveInt(constraints.get("description_max_length"), DEFAULT_DESCRIPTION_MAX));
        return limits;
    }

    /**
     * 给模型的 preferred 区间（仅指导，不在本票做强制 min 校验）。
     */
    public static Map<String, Map<String, Integer>> preferredLengthRanges(
            Map<String, Integer> lengthLimits, Map<String, Object> targetLanguage) {
        String label = clean(targetLanguage == null ? null : targetLanguage.get("label"));
        String labelLower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        int[] primary;
        int[] headline;
        int[] description;
        if (labelLower.contains("chinese") || labelLower.contains("japanese") || labelLower.contains("korean")) {
            primary = new int[] {80, 150};
            headline = new int[] {10, 18};
            description = new int[] {15, 30};
        } else if (labelLower.contains("hindi") || labelLower.contains("thai")) {
            primary = new int[] {80, 180};
            headline = new int[] {14, 28};
            description = new int[] {14, 30};
        } else {
            primary = new int[] {220, 420};
            headline = new int[] {28, 40};
            description = new int[] {35, 60};
        }
        Map<String, Map<String, Integer>> ranges = new LinkedHashMap<>();
        ranges.put("primary_text", range(primary[0], primary[1], lengthLimits.get("primary_text")));
        ranges.put("headline", range(headline[0], headline[1], lengthLimits.get("headline")));
        ranges.put("description", range(description[0], description[1], lengthLimits.get("description")));
        return ranges;
    }

    private static Map<String, Integer> range(int preferredMin, int preferredMax, Integer hardMax) {
        int limit = hardMax == null ? preferredMax : hardMax;
        int targetMax = Math.min(preferredMax, limit);
        int targetMin = targetMax >= preferredMin ? preferredMin : Math.max(1, (int) (targetMax * 0.6));
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("min", targetMin);
        out.put("max", targetMax);
        return out;
    }

    private static int positiveInt(Object value, int defaultValue) {
        try {
            int parsed;
            if (value instanceof Number n) {
                parsed = n.intValue();
            } else if (value == null) {
                return defaultValue;
            } else {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            }
            return parsed > 0 ? Math.max(parsed, 10) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
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

    /**
     * 消费者卖点列表：去重、截断，并丢弃明显的运营/素材备注。
     */
    static List<String> consumerSellingPoints(Object value) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (value == null) {
            return result;
        }
        Iterable<?> items = value instanceof List<?> list ? list : List.of(value);
        for (Object item : items) {
            String text = trim(clean(item), MAX_SELLING_POINT);
            if (text == null || isOperatorNoteSellingPoint(text)) {
                continue;
            }
            String key = text.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                result.add(text);
            }
        }
        return result;
    }

    static boolean isOperatorNoteSellingPoint(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return OPERATOR_NOTE_POINT.matcher(text.strip()).find();
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
