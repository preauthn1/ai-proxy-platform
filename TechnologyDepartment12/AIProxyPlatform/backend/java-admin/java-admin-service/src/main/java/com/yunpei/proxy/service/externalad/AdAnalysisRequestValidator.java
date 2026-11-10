package com.yunpei.proxy.service.externalad;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部广告效果分析请求校验（对齐 Python {@code ExternalAdPerformanceAnalysisCreate}）。
 *
 * <p>就地规范化枚举与 URL，便于后续幂等 hash 与落库使用同一份 payload。
 *
 * @author yunpei
 */
public final class AdAnalysisRequestValidator {

    static final Set<String> CBO_BID_STRATEGIES =
            Set.of("LOWEST_COST_WITHOUT_CAP", "LOWEST_COST_WITH_BID_CAP", "LOWEST_COST_WITH_MIN_ROAS");
    static final Set<String> ADSET_BILLING_EVENTS = Set.of("IMPRESSIONS", "LINK_CLICKS");
    static final Set<String> ADSET_OPTIMIZATION_GOALS = Set.of(
            "LINK_CLICKS", "LANDING_PAGE_VIEWS", "OFFSITE_CONVERSIONS", "THRUPLAY", "APP_INSTALLS", "LEAD_GENERATION");
    static final Set<String> ADSET_GENDERS = Set.of("ALL", "MALE", "FEMALE");
    static final Set<String> ADSET_CUSTOM_EVENT_TYPES = Set.of(
            "COMPLETE_REGISTRATION",
            "PURCHASE",
            "ADD_TO_CART",
            "INITIATED_CHECKOUT",
            "SEARCH",
            "ADD_PAYMENT_INFO",
            "FIRST_RECHARGE");
    static final Set<String> INSIGHT_DELIVERY_STATUSES = Set.of("ACTIVE", "PAUSED");

    private static final Map<String, String> CREATIVE_BTN_TYPE_ALIASES = new LinkedHashMap<>();
    private static final Pattern SWITCH_TIME = Pattern.compile("^(\\d{1,2})-(\\d{1,2})$");
    private static final int MAX_EXTERNAL_REQUEST_ID = 128;

    static {
        CREATIVE_BTN_TYPE_ALIASES.put("VIDEO CALL", "VIDEO_CALL");
        CREATIVE_BTN_TYPE_ALIASES.put("VIDEO_CALL", "VIDEO_CALL");
        CREATIVE_BTN_TYPE_ALIASES.put("INSTALL MOBILE APP", "INSTALL_MOBILE_APP");
        CREATIVE_BTN_TYPE_ALIASES.put("INSTALL_MOBILE_APP", "INSTALL_MOBILE_APP");
        CREATIVE_BTN_TYPE_ALIASES.put("USE MOBILE APP", "USE_MOBILE_APP");
        CREATIVE_BTN_TYPE_ALIASES.put("USE_MOBILE_APP", "USE_MOBILE_APP");
        CREATIVE_BTN_TYPE_ALIASES.put("MOBILE DOWNLOAD", "MOBILE_DOWNLOAD");
        CREATIVE_BTN_TYPE_ALIASES.put("MOBILE_DOWNLOAD", "MOBILE_DOWNLOAD");
        CREATIVE_BTN_TYPE_ALIASES.put("BOOK TRAVEL", "BOOK_TRAVEL");
        CREATIVE_BTN_TYPE_ALIASES.put("BOOK_TRAVEL", "BOOK_TRAVEL");
        CREATIVE_BTN_TYPE_ALIASES.put("LISTEN MUSIC", "LISTEN_MUSIC");
        CREATIVE_BTN_TYPE_ALIASES.put("LISTEN_MUSIC", "LISTEN_MUSIC");
        CREATIVE_BTN_TYPE_ALIASES.put("观看视频", "WATCH_MORE");
        CREATIVE_BTN_TYPE_ALIASES.put("WATCH MORE", "WATCH_MORE");
        CREATIVE_BTN_TYPE_ALIASES.put("WATCH_MORE", "WATCH_MORE");
        CREATIVE_BTN_TYPE_ALIASES.put("了解更多", "LEARN_MORE");
        CREATIVE_BTN_TYPE_ALIASES.put("LEARN MORE", "LEARN_MORE");
        CREATIVE_BTN_TYPE_ALIASES.put("LEARN_MORE", "LEARN_MORE");
    }

    private AdAnalysisRequestValidator() {}

    /**
     * 校验并就地规范化 payload。
     *
     * @param payload 可变请求对象
     */
    public static void validate(Map<String, Object> payload) {
        if (payload == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "request body is invalid");
        }
        String externalRequestId = clean(payload.get("external_request_id"));
        if (externalRequestId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "external_request_id must not be blank");
        }
        if (externalRequestId.length() > MAX_EXTERNAL_REQUEST_ID) {
            throw BizException.of(ResultCode.PARAM_INVALID, "external_request_id must be at most 128 characters");
        }
        payload.put("external_request_id", externalRequestId);

        Object sourceType = payload.get("source_type");
        if (sourceType == null || clean(sourceType) == null) {
            payload.put("source_type", "external");
        }

        Map<String, Object> creative = requireObject(payload, "creative");
        validateCreativeMedia(creative);
        normalizeCreativeButtonType(creative);

        validateInsightContract(requireObject(payload, "insight"), "insight");
        Object siblingsRaw = payload.get("siblings");
        if (siblingsRaw != null) {
            if (!(siblingsRaw instanceof List<?> siblings)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "siblings must be an array");
            }
            List<Object> copiedSiblings = new java.util.ArrayList<>();
            int index = 0;
            for (Object sibling : siblings) {
                if (sibling instanceof Map<?, ?> siblingMap) {
                    Map<String, Object> mutableSibling = copyMap(siblingMap);
                    Object insight = mutableSibling.get("insight");
                    if (insight instanceof Map<?, ?> insightRaw) {
                        Map<String, Object> insightMap = copyMap(insightRaw);
                        validateInsightContract(insightMap, "siblings[" + index + "].insight");
                        mutableSibling.put("insight", insightMap);
                    }
                    copiedSiblings.add(mutableSibling);
                } else {
                    copiedSiblings.add(sibling);
                }
                index++;
            }
            payload.put("siblings", copiedSiblings);
        }

        Map<String, Object> campaign = requireObject(payload, "campaign");
        if (campaign.containsKey("bid_strategy") && campaign.get("bid_strategy") != null) {
            campaign.put(
                    "bid_strategy",
                    normalizeEnum(campaign.get("bid_strategy"), "campaign.bid_strategy", CBO_BID_STRATEGIES));
        }
        normalizeSwitchTime(campaign, "campaign.switch_time");
        validateAdsetDeliveryConfiguration(requireObject(payload, "adset"));
    }

    static Map<String, Object> requireObject(Map<String, Object> payload, String field) {
        Object raw = payload.get(field);
        if (raw == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            payload.put(field, empty);
            return empty;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " must be an object");
        }
        Map<String, Object> typed = copyMap(map);
        payload.put(field, typed);
        return typed;
    }

    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            typed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return typed;
    }

    private static void validateCreativeMedia(Map<String, Object> creative) {
        String creativeType = clean(creative.get("creative_type"));
        if (creativeType == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "creative.creative_type must be image, video, or carousel");
        }
        creativeType = creativeType.toLowerCase(Locale.ROOT);
        if (!Set.of("image", "video", "carousel").contains(creativeType)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "creative.creative_type must be image, video, or carousel");
        }
        creative.put("creative_type", creativeType);
        if ("carousel".equals(creativeType)) {
            Object imageUrls = creative.get("image_urls");
            if (imageUrls == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "creative.image_urls is required");
            }
            if (!(imageUrls instanceof List<?> urls)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "creative.image_urls must be an array");
            }
            if (urls.size() < 2 || urls.size() > 10) {
                throw BizException.of(
                        ResultCode.PARAM_INVALID, "creative.image_urls must contain between 2 and 10 image URLs");
            }
            java.util.ArrayList<String> cleaned = new java.util.ArrayList<>();
            int index = 0;
            for (Object imageUrl : urls) {
                String url = imageUrl instanceof String s ? s.strip() : null;
                if (url == null || url.isEmpty()) {
                    throw BizException.of(
                            ResultCode.PARAM_INVALID,
                            "creative.image_urls[" + index + "] must be a complete HTTP or HTTPS URL");
                }
                validateCompleteHttpUrl(url, "creative.image_urls[" + index + "]");
                cleaned.add(url);
                index++;
            }
            creative.put("image_urls", cleaned);
            return;
        }
        String sourceField = "image".equals(creativeType) ? "image_url" : "video_url";
        String sourceUrl = creative.get(sourceField) instanceof String s ? s.strip() : null;
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "creative." + sourceField + " is required");
        }
        validateCompleteHttpUrl(sourceUrl, "creative." + sourceField);
        creative.put(sourceField, sourceUrl);
    }

    private static void normalizeCreativeButtonType(Map<String, Object> creative) {
        Object btnType = creative.get("btn_type");
        if (btnType == null) {
            return;
        }
        String key = String.join(
                " ",
                String.valueOf(btnType)
                        .strip()
                        .toUpperCase(Locale.ROOT)
                        .replace('_', ' ')
                        .split("\\s+"));
        // 中文别名不能走 upper+空格规范化；先用原始 strip，再试 upper 空格键
        String raw = String.valueOf(btnType).strip();
        String normalized = CREATIVE_BTN_TYPE_ALIASES.get(raw);
        if (normalized == null) {
            normalized = CREATIVE_BTN_TYPE_ALIASES.get(key);
        }
        if (normalized == null) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "creative.btn_type must be one of: "
                            + String.join(
                                    ", ",
                                    Set.copyOf(CREATIVE_BTN_TYPE_ALIASES.values()).stream()
                                            .sorted()
                                            .toList()));
        }
        creative.put("btn_type", normalized);
    }

    private static void validateInsightContract(Map<String, Object> insight, String fieldName) {
        Object status = insight.get("status");
        if (status != null) {
            insight.put("status", normalizeEnum(status, fieldName + ".status", INSIGHT_DELIVERY_STATUSES));
        }
    }

    private static void validateAdsetDeliveryConfiguration(Map<String, Object> adset) {
        if (adset.containsKey("bid_strategy")) {
            if (!isPositiveDecimal(adset.get("daily_budget"))) {
                throw BizException.of(
                        ResultCode.PARAM_INVALID, "adset.bid_strategy requires a non-null adset.daily_budget");
            }
            adset.put(
                    "bid_strategy", normalizeEnum(adset.get("bid_strategy"), "adset.bid_strategy", CBO_BID_STRATEGIES));
        }
        if (adset.containsKey("billing_event") && adset.get("billing_event") != null) {
            adset.put(
                    "billing_event",
                    normalizeEnum(adset.get("billing_event"), "adset.billing_event", ADSET_BILLING_EVENTS));
        }
        if (adset.containsKey("optimization_goal") && adset.get("optimization_goal") != null) {
            adset.put(
                    "optimization_goal",
                    normalizeEnum(adset.get("optimization_goal"), "adset.optimization_goal", ADSET_OPTIMIZATION_GOALS));
        }
        if (adset.containsKey("genders") && adset.get("genders") != null) {
            adset.put("genders", normalizeEnum(adset.get("genders"), "adset.genders", ADSET_GENDERS));
        }
        Object customEventType = adset.get("custom_event_type");
        if (customEventType != null) {
            String normalized = String.valueOf(customEventType)
                    .strip()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_');
            if (!ADSET_CUSTOM_EVENT_TYPES.contains(normalized)) {
                throw BizException.of(
                        ResultCode.PARAM_INVALID,
                        "adset.custom_event_type must be one of: "
                                + ADSET_CUSTOM_EVENT_TYPES.stream()
                                        .sorted()
                                        .reduce((a, b) -> a + ", " + b)
                                        .orElse(""));
            }
            adset.put("custom_event_type", normalized);
        }
        BigDecimal scale = null;
        BigDecimal stop = null;
        if (adset.containsKey("scale_threshold") && adset.get("scale_threshold") != null) {
            scale = nonNegativeDecimal(adset.get("scale_threshold"), "adset.scale_threshold");
            adset.put("scale_threshold", scale.stripTrailingZeros().toPlainString());
        }
        if (adset.containsKey("stop_threshold") && adset.get("stop_threshold") != null) {
            stop = nonNegativeDecimal(adset.get("stop_threshold"), "adset.stop_threshold");
            adset.put("stop_threshold", stop.stripTrailingZeros().toPlainString());
        }
        if (scale != null && stop != null && scale.compareTo(stop) >= 0) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "adset.scale_threshold must be lower than adset.stop_threshold");
        }
        normalizeSwitchTime(adset, "adset.switch_time");
    }

    private static void normalizeSwitchTime(Map<String, Object> record, String fieldName) {
        Object switchTime = record.get("switch_time");
        if (switchTime == null) {
            return;
        }
        Matcher match = SWITCH_TIME.matcher(String.valueOf(switchTime).strip());
        if (!match.matches()) {
            throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " must use HH-HH with hours from 0 to 23");
        }
        int startHour = Integer.parseInt(match.group(1));
        int endHour = Integer.parseInt(match.group(2));
        if (startHour > 23 || endHour > 23) {
            throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " must use HH-HH with hours from 0 to 23");
        }
        if (startHour == endHour && (startHour != 0 || endHour != 0)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    fieldName + " must not use the same start and end hour; use 0-0 for no limit");
        }
        record.put("switch_time", startHour + "-" + endHour);
    }

    private static String normalizeEnum(Object value, String fieldName, Set<String> allowed) {
        String normalized = String.valueOf(value).strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    fieldName + " must be one of: "
                            + allowed.stream()
                                    .sorted()
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse(""));
        }
        return normalized;
    }

    private static void validateCompleteHttpUrl(String value, String fieldName) {
        try {
            URI parsed = URI.create(value.strip());
            String scheme = parsed.getScheme();
            String host = parsed.getHost();
            if (scheme == null
                    || host == null
                    || host.isBlank()
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " must be a complete HTTP or HTTPS URL");
            }
        } catch (IllegalArgumentException ex) {
            throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " must be a complete HTTP or HTTPS URL");
        }
    }

    private static BigDecimal nonNegativeDecimal(Object value, String fieldName) {
        try {
            BigDecimal decimal =
                    new BigDecimal(String.valueOf(value).replace(",", "").strip());
            if (decimal.signum() < 0) {
                throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " must be a non-negative number");
            }
            return decimal;
        } catch (NumberFormatException ex) {
            throw BizException.of(ResultCode.PARAM_INVALID, fieldName + " must be a non-negative number");
        }
    }

    private static boolean isPositiveDecimal(Object value) {
        if (value == null) {
            return false;
        }
        try {
            return nonNegativeDecimal(value, "adset.daily_budget").compareTo(BigDecimal.ZERO) > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }
}
