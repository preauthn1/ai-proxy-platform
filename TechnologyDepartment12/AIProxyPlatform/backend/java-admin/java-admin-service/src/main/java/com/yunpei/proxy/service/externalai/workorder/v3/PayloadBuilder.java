package com.yunpei.proxy.service.externalai.workorder.v3;

import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.BID_WITHOUT_CAP;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.BID_WITH_CAP;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_APP_PROMOTION;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_ENGAGEMENT;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_SALES;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_TRAFFIC;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.REQUIRED_BINDINGS;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_FALLBACK;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_LLM_FALLBACK;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.SALES_BTN_BY_EVENT;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.SCHEMA_VERSION;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.STATUS_PAUSED;

import com.yunpei.proxy.common.time.TimeZones;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 组装 V3 campaign/adset/creative 载荷（对齐 Python {@code payload_builder}）。
 *
 * @author yunpei
 */
public final class PayloadBuilder {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMddHHmm");
    private static final Set<String> KEEP_EMPTY_KEYS = Set.of(
            "campaign_payload",
            "adset_payload",
            "creative_payload",
            "targeting_suggestions",
            "review",
            "field_decisions");

    private PayloadBuilder() {}

    public static Map<String, Object> buildV3Result(
            String requestId,
            String workOrderText,
            ObjectiveDetection detection,
            ExtractedFields fields,
            Map<String, Object> llmHints) {
        String objective = detection.detectedObjective();
        Map<String, Object> review = baseReview(detection);
        Map<String, Object> bid = resolveBid(fields, review);

        Map<String, Object> campaign = campaignPayload(objective, fields, bid);
        Map<String, Object> adset = adsetPayload(objective, workOrderText, fields, detection, bid, review, llmHints);
        Map<String, Object> creative = creativePayload(objective, fields, review);

        Map<String, Object> targeting = new LinkedHashMap<>();
        targeting.put("keywords", fields.getKeywords() == null ? List.of() : fields.getKeywords());
        String desc = fields.getTargetingDescription();
        if (desc == null || desc.isBlank()) {
            desc = fields.getAudience() == null ? "" : fields.getAudience();
        }
        targeting.put("description", desc);

        if (RULE_FALLBACK.equals(detection.matchedRule())) {
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) review.get("warnings");
            warnings.add("无法可靠识别广告目标，已兜底为流量；请人工确认");
            review.put("requires_user_review", true);
        }

        if ("llm_fallback".equals(fields.getSources().get("run_hours"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> decisions = (Map<String, Object>) review.get("field_decisions");
            Map<String, Object> runHours = new LinkedHashMap<>();
            runHours.put("source", "llm_fallback");
            runHours.put(
                    "reason",
                    "run_start_time=" + fields.getRunStartTime() + ", run_stop_time=" + fields.getRunStopTime());
            decisions.put("run_hours", runHours);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", SCHEMA_VERSION);
        result.put("request_id", requestId);
        result.put("product_name", fields.getProductName());
        result.put("brand_name", fields.getBrandName());
        result.put("detected_objective", objective);
        result.put("objective_detection", detection.objectiveDetectionPayload());
        result.put("campaign_payload", campaign);
        result.put("adset_payload", adset);
        result.put("creative_payload", creative);
        result.put("targeting_suggestions", targeting);
        result.put("required_bindings", new ArrayList<>(REQUIRED_BINDINGS.getOrDefault(objective, List.of())));
        result.put("review", review);
        return omitEmpty(result);
    }

    private static Map<String, Object> baseReview(ObjectiveDetection detection) {
        String source = RULE_LLM_FALLBACK.equals(detection.matchedRule()) ? "llm_fallback" : "rule";
        Map<String, Object> objective = new LinkedHashMap<>();
        objective.put("source", source);
        objective.put(
                "reason", "matched_rule=" + detection.matchedRule() + ", matched_value=" + detection.matchedValue());
        Map<String, Object> decisions = new LinkedHashMap<>();
        decisions.put("objective", objective);
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("requires_user_review", RULE_LLM_FALLBACK.equals(detection.matchedRule()));
        review.put("missing_fields", new ArrayList<String>());
        review.put("warnings", new ArrayList<String>());
        review.put("field_decisions", decisions);
        return review;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveBid(ExtractedFields fields, Map<String, Object> review) {
        Map<String, Object> decisions = (Map<String, Object>) review.get("field_decisions");
        if (fields.isBidWantsCap() && fields.getBidAmount() != null) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("source", "work_order");
            decision.put("reason", "工单提供出价上限金额 " + fields.getBidAmount());
            decisions.put("bid_strategy", decision);
            Map<String, Object> bid = new LinkedHashMap<>();
            bid.put("strategy", BID_WITH_CAP);
            bid.put("amount", fields.getBidAmount());
            return bid;
        }
        if (fields.isBidWantsCap() && fields.getBidAmount() == null) {
            review.put("requires_user_review", true);
            ((List<String>) review.get("missing_fields")).add("bid_amount");
            ((List<String>) review.get("warnings")).add("工单要求设置出价上限但未提供金额，已降级为最低成本无上限；请在创建前确认");
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("source", "safety_fallback");
            decision.put("reason", "缺少有效 bid_amount，不允许模型编造金额");
            decisions.put("bid_strategy", decision);
            Map<String, Object> bid = new LinkedHashMap<>();
            bid.put("strategy", BID_WITHOUT_CAP);
            bid.put("amount", null);
            return bid;
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("source", fields.getSources().containsKey("daily_budget") ? "work_order" : "default");
        decision.put("reason", "未给出价上限，使用最低成本无上限");
        decisions.put("bid_strategy", decision);
        Map<String, Object> bid = new LinkedHashMap<>();
        bid.put("strategy", BID_WITHOUT_CAP);
        bid.put("amount", null);
        return bid;
    }

    public static String generateAdEntityName() {
        return generateAdEntityName(TimeZones.now());
    }

    public static String generateAdEntityName(LocalDateTime now) {
        StringBuilder prefix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            prefix.append(UPPER[RANDOM.nextInt(UPPER.length)]);
        }
        return prefix + "-" + STAMP.format(now);
    }

    private static Map<String, Object> campaignPayload(
            String objective, ExtractedFields fields, Map<String, Object> bid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", generateAdEntityName());
        payload.put("objective", objective);
        payload.put("budget_type", fields.getBudgetType());
        payload.put("status", STATUS_PAUSED);
        if ("campaign".equals(fields.getBudgetType())) {
            if (fields.getDailyBudget() != null) {
                payload.put("daily_budget", fields.getDailyBudget());
            }
            payload.put("bid_strategy", bid.get("strategy"));
            if (bid.get("amount") != null) {
                payload.put("bid_amount", bid.get("amount"));
            }
        }
        if (fields.getSpecialAdCategories() != null
                && !fields.getSpecialAdCategories().isEmpty()) {
            payload.put("special_ad_categories", fields.getSpecialAdCategories());
        }
        if (fields.getRunStartTime() != null) {
            payload.put("run_start_time", fields.getRunStartTime());
        }
        if (fields.getRunStopTime() != null) {
            payload.put("run_stop_time", fields.getRunStopTime());
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> adsetPayload(
            String objective,
            String workOrderText,
            ExtractedFields fields,
            ObjectiveDetection detection,
            Map<String, Object> bid,
            Map<String, Object> review,
            Map<String, Object> llmHints) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", generateAdEntityName());
        payload.put("billing_event", "IMPRESSIONS");
        payload.put("status", STATUS_PAUSED);

        if ("adset".equals(fields.getBudgetType())) {
            if (fields.getDailyBudget() != null) {
                payload.put("daily_budget", fields.getDailyBudget());
            }
            payload.put("bid_strategy", bid.get("strategy"));
            if (bid.get("amount") != null) {
                payload.put("bid_amount", bid.get("amount"));
            }
        }

        if (fields.getCountries() != null && !fields.getCountries().isEmpty()) {
            payload.put("countries", fields.getCountries());
        }
        if (fields.getAgeMin() != null) {
            payload.put("age_min", fields.getAgeMin());
        }
        if (fields.getAgeMax() != null) {
            payload.put("age_max", fields.getAgeMax());
        }
        if (fields.getGender() != null) {
            payload.put("gender", fields.getGender());
        }
        if (fields.getDevicePlatforms() != null && !fields.getDevicePlatforms().isEmpty()) {
            payload.put("device_platforms", fields.getDevicePlatforms());
        }
        if (fields.getPlacementMode() != null) {
            payload.put("placement_mode", fields.getPlacementMode());
        }
        if ("manual".equals(fields.getPlacementMode())
                && fields.getPublisherPlatforms() != null
                && !fields.getPublisherPlatforms().isEmpty()) {
            payload.put("publisher_platforms", fields.getPublisherPlatforms());
        }
        if (fields.getStartType() != null) {
            payload.put("start_type", fields.getStartType());
        }
        if (fields.getStartTime() != null) {
            payload.put("start_time", fields.getStartTime());
        }
        if (fields.getEndTime() != null) {
            payload.put("end_time", fields.getEndTime());
        }

        Map<String, Object> decisions = (Map<String, Object>) review.get("field_decisions");
        List<String> missing = (List<String>) review.get("missing_fields");

        if (OBJECTIVE_ENGAGEMENT.equals(objective)) {
            Map<String, Object> channels = ObjectiveDetector.messagingChannelMapping(workOrderText, llmHints);
            payload.put("conversion_location", "MESSAGING");
            payload.put("messaging_channels", channels.get("messaging_channels"));
            payload.put("destination_type", channels.get("destination_type"));
            payload.put("messagePlatform", channels.get("messagePlatform"));
            payload.put(
                    "whatsAppAreaCode",
                    channels.get("whatsAppAreaCode") == null ? "" : channels.get("whatsAppAreaCode"));
            payload.put("whatsAppNumber", channels.get("whatsAppNumber") == null ? "" : channels.get("whatsAppNumber"));
            payload.put("optimization_goal", "CONVERSATIONS");
            payload.put("attribution_click_days", 1);
            payload.put("attribution_view_days", 0);
        } else if (OBJECTIVE_APP_PROMOTION.equals(objective)) {
            String[] optEvent = appOptimization(fields, decisions);
            payload.put("destination_type", "APP");
            payload.put("optimization_goal", optEvent[0]);
            if (fields.getMobileAppStore() != null) {
                payload.put("mobile_app_store", fields.getMobileAppStore());
            }
            if (fields.getObjectStoreUrl() != null) {
                payload.put("object_store_url", fields.getObjectStoreUrl());
            } else {
                missing.add("object_store_url");
                review.put("requires_user_review", true);
            }
            if (optEvent[1] != null) {
                payload.put("custom_event_type", optEvent[1]);
            }
            if (fields.getDevicePlatforms() == null
                    || fields.getDevicePlatforms().isEmpty()) {
                payload.put("device_platforms", List.of("mobile"));
            }
            if (fields.getAttributionClickDays() != null) {
                payload.put("attribution_click_days", fields.getAttributionClickDays());
            }
            if (fields.getAttributionViewDays() != null) {
                payload.put("attribution_view_days", fields.getAttributionViewDays());
            }
        } else if (OBJECTIVE_TRAFFIC.equals(objective)) {
            String opt = trafficOptimization(detection);
            payload.put("conversion_location", "WEBSITE");
            payload.put("destination_type", "WEBSITE");
            payload.put("optimization_goal", opt);
            payload.put(
                    "attribution_click_days",
                    fields.getAttributionClickDays() != null ? fields.getAttributionClickDays() : 1);
            payload.put(
                    "attribution_view_days",
                    fields.getAttributionViewDays() != null ? fields.getAttributionViewDays() : 0);
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put(
                    "source",
                    detection.matchedValue() != null
                                    && !detection.matchedValue().isBlank()
                            ? "work_order"
                            : "default");
            decision.put(
                    "reason",
                    "traffic optimization from "
                            + (detection.matchedValue() == null
                                            || detection.matchedValue().isBlank()
                                    ? "fallback"
                                    : detection.matchedValue()));
            decisions.put("optimization_goal", decision);
        } else if (OBJECTIVE_SALES.equals(objective)) {
            String[] optEvent = salesOptimization(fields, decisions);
            payload.put("conversion_location", "WEBSITE");
            payload.put("destination_type", "WEBSITE");
            payload.put("optimization_goal", optEvent[0]);
            payload.put(
                    "attribution_click_days",
                    fields.getAttributionClickDays() != null ? fields.getAttributionClickDays() : 7);
            payload.put(
                    "attribution_view_days",
                    fields.getAttributionViewDays() != null ? fields.getAttributionViewDays() : 1);
            if (optEvent[1] != null) {
                payload.put("custom_event_type", optEvent[1]);
            } else {
                missing.add("custom_event_type");
                review.put("requires_user_review", true);
            }
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> creativePayload(
            String objective, ExtractedFields fields, Map<String, Object> review) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", generateAdEntityName());
        String type = fields.getCreativeType();
        payload.put("type", "image".equals(type) || "video".equals(type) ? type : "image");
        Map<String, Object> decisions = (Map<String, Object>) review.get("field_decisions");
        List<String> missing = (List<String>) review.get("missing_fields");

        if (OBJECTIVE_ENGAGEMENT.equals(objective)) {
            payload.put("btn_type", "MESSAGE_PAGE");
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("source", "objective_rule");
            decision.put("reason", "消息互动不回填网站 link");
            decisions.put("creative_link", decision);
        } else if (OBJECTIVE_APP_PROMOTION.equals(objective)) {
            payload.put("btn_type", "LEARN_MORE");
        } else if (OBJECTIVE_TRAFFIC.equals(objective)) {
            payload.put("btn_type", "LEARN_MORE");
            if (fields.getLandingUrl() != null) {
                payload.put("link", fields.getLandingUrl());
            } else {
                missing.add("landing_url");
                review.put("requires_user_review", true);
            }
        } else if (OBJECTIVE_SALES.equals(objective)) {
            String event = fields.getCustomEventType() == null ? "" : fields.getCustomEventType();
            payload.put("btn_type", SALES_BTN_BY_EVENT.getOrDefault(event, "LEARN_MORE"));
            if (fields.getLandingUrl() != null) {
                payload.put("link", fields.getLandingUrl());
            } else {
                missing.add("landing_url");
                review.put("requires_user_review", true);
            }
        }
        return payload;
    }

    private static String[] appOptimization(ExtractedFields fields, Map<String, Object> decisions) {
        String textEvent =
                fields.getEventName() == null ? "" : fields.getEventName().toLowerCase(Locale.ROOT);
        boolean valueLike = textEvent.contains("value")
                || textEvent.contains("roas")
                || textEvent.contains("价值")
                || textEvent.contains("收入")
                || (fields.getEventName() != null
                        && fields.getEventName().toUpperCase(Locale.ROOT).contains("ROAS"));
        if (valueLike) {
            String event = FieldExtract.resolveAppCustomEvent(fields);
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("source", "work_order");
            decision.put("reason", "价值/ROAS 语义，选择 VALUE");
            decisions.put("optimization_goal", decision);
            return new String[] {"VALUE", event};
        }
        String event = FieldExtract.resolveAppCustomEvent(fields);
        if (event != null) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("source", "work_order");
            decision.put("reason", "应用事件 " + event + "，选择 OFFSITE_CONVERSIONS_APP");
            decisions.put("optimization_goal", decision);
            return new String[] {"OFFSITE_CONVERSIONS_APP", event};
        }
        if (fields.getEventName() != null) {
            String lower = fields.getEventName().toLowerCase(Locale.ROOT);
            if (lower.contains("click") || lower.contains("点击")) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("source", "work_order");
                decision.put("reason", "明确点击，选择 LINK_CLICKS");
                decisions.put("optimization_goal", decision);
                return new String[] {"LINK_CLICKS", null};
            }
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("source", "default");
        decision.put("reason", "应用推广默认 APP_INSTALLS");
        decisions.put("optimization_goal", decision);
        return new String[] {"APP_INSTALLS", null};
    }

    private static String trafficOptimization(ObjectiveDetection detection) {
        String value =
                detection.matchedValue() == null ? "" : detection.matchedValue().toLowerCase(Locale.ROOT);
        if (value.contains("落地页")
                || value.contains("访问")
                || value.contains("浏览")
                || value.contains("landing")
                || value.contains("visit")
                || value.contains("view")
                || value.contains("lpv")) {
            return "LANDING_PAGE_VIEWS";
        }
        return "LINK_CLICKS";
    }

    private static String[] salesOptimization(ExtractedFields fields, Map<String, Object> decisions) {
        String eventName = fields.getEventName() == null ? "" : fields.getEventName();
        String lower = eventName.toLowerCase(Locale.ROOT);
        if (lower.contains("roas")
                || lower.contains("价值")
                || lower.contains("收入")
                || lower.contains("value")
                || (fields.getCustomEventType() == null
                        && eventName.toUpperCase(Locale.ROOT).contains("ROAS"))) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("source", "work_order");
            decision.put("reason", "价值/ROAS 语义，选择 VALUE");
            decisions.put("optimization_goal", decision);
            return new String[] {"VALUE", fields.getCustomEventType()};
        }
        String event = fields.getCustomEventType();
        if (event == null && fields.getEventName() != null) {
            String upper = fields.getEventName().toUpperCase(Locale.ROOT);
            if (upper.contains("PURCHASE") || fields.getEventName().contains("购买")) {
                event = "PURCHASE";
            } else if (fields.getEventName().contains("注册")) {
                event = "COMPLETE_REGISTRATION";
            } else if (fields.getEventName().contains("首充")) {
                event = "PURCHASE";
            }
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("source", "work_order");
        decision.put("reason", "网站转化事件，选择 OFFSITE_CONVERSIONS");
        decisions.put("optimization_goal", decision);
        return new String[] {"OFFSITE_CONVERSIONS", event};
    }

    @SuppressWarnings("unchecked")
    static Object omitEmptyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object item = entry.getValue();
                if (item == null) {
                    continue;
                }
                Object nested = omitEmptyValue(item);
                if (nested == null) {
                    continue;
                }
                if (nested instanceof Map<?, ?> nestedMap && nestedMap.isEmpty() && !KEEP_EMPTY_KEYS.contains(key)) {
                    continue;
                }
                cleaned.put(key, nested);
            }
            return cleaned;
        }
        if (value instanceof List<?> list) {
            List<Object> cleaned = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                cleaned.add(omitEmptyValue(item));
            }
            return cleaned;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> omitEmpty(Map<String, Object> value) {
        return (Map<String, Object>) omitEmptyValue(value);
    }
}
