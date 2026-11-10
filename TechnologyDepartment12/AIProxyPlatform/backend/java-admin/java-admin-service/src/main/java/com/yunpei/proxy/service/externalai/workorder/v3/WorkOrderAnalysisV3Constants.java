package com.yunpei.proxy.service.externalai.workorder.v3;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单分析 V3 常量（对齐 Python {@code work_order_analysis_v3.constants}）。
 *
 * @author yunpei
 */
public final class WorkOrderAnalysisV3Constants {

    public static final String SCHEMA_VERSION = "3.0";

    public static final String OBJECTIVE_ENGAGEMENT = "OUTCOME_ENGAGEMENT";
    public static final String OBJECTIVE_APP_PROMOTION = "OUTCOME_APP_PROMOTION";
    public static final String OBJECTIVE_TRAFFIC = "OUTCOME_TRAFFIC";
    public static final String OBJECTIVE_SALES = "OUTCOME_SALES";

    public static final String RULE_SOCIAL = "social_or_messaging_destination";
    public static final String RULE_APP = "application_destination";
    public static final String RULE_TRAFFIC = "traffic_event_or_no_event";
    public static final String RULE_CONVERSION = "conversion_event";
    public static final String RULE_FALLBACK = "fallback_traffic";
    public static final String RULE_LLM_FALLBACK = "llm_fallback";

    public static final String BID_WITHOUT_CAP = "LOWEST_COST_WITHOUT_CAP";
    public static final String BID_WITH_CAP = "LOWEST_COST_WITH_BID_CAP";
    public static final String STATUS_PAUSED = "PAUSED";

    public static final Map<String, List<String>> REQUIRED_BINDINGS = Map.of(
            OBJECTIVE_ENGAGEMENT, List.of("auth_user", "ad_account", "facebook_page", "asset"),
            OBJECTIVE_APP_PROMOTION, List.of("auth_user", "ad_account", "facebook_page", "application", "asset"),
            OBJECTIVE_TRAFFIC, List.of("auth_user", "ad_account", "facebook_page", "asset"),
            OBJECTIVE_SALES, List.of("auth_user", "ad_account", "facebook_page", "pixel", "asset"));

    public static final Set<String> APP_EVENT_TYPES = Set.of(
            "COMPLETE_REGISTRATION",
            "CONTENT_VIEW",
            "SEARCH",
            "RATE",
            "TUTORIAL_COMPLETION",
            "ADD_TO_CART",
            "ADD_TO_WISHLIST",
            "INITIATED_CHECKOUT",
            "ADD_PAYMENT_INFO",
            "PURCHASE",
            "LEAD",
            "LEVEL_ACHIEVED",
            "ACHIEVEMENT_UNLOCKED",
            "SPENT_CREDITS",
            "CONTACT",
            "CUSTOMIZE_PRODUCT",
            "DONATE",
            "FIND_LOCATION",
            "SCHEDULE",
            "START_TRIAL",
            "SUBMIT_APPLICATION",
            "SUBSCRIBE");

    public static final Map<String, String> SALES_BTN_BY_EVENT = Map.of(
            "PURCHASE", "SHOP_NOW",
            "ADD_TO_CART", "SHOP_NOW",
            "INITIATED_CHECKOUT", "SHOP_NOW",
            "COMPLETE_REGISTRATION", "SIGN_UP");

    private WorkOrderAnalysisV3Constants() {}
}
