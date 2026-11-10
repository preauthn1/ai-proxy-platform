package com.yunpei.proxy.service.externalai.workorder;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 自定义转化事件归一化（对齐 Python {@code custom_event_types}）。
 *
 * @author yunpei
 */
public final class CustomEventTypes {

    private static final Map<String, String> CUSTOM_EVENT_TYPES = Map.of(
            "complete_registration", "COMPLETE_REGISTRATION",
            "purchase", "PURCHASE",
            "add_to_cart", "ADD_TO_CART",
            "initiated_checkout", "INITIATED_CHECKOUT",
            "search", "SEARCH",
            "add_payment_info", "ADD_PAYMENT_INFO",
            "first_recharge", "first_recharge");

    private static final Set<String> COMPLETE_REGISTRATION_ALIASES =
            Set.of("complete_registration", "register", "signup");
    private static final Set<String> LEAD_ALIASES = Set.of("lead", "leads");
    private static final Set<String> PURCHASE_ALIASES = Set.of("purchase", "shop", "shopping", "buy", "order", "sales");
    private static final Set<String> ADD_TO_CART_ALIASES = Set.of("add_to_cart", "addtocart", "cart");
    private static final Set<String> INITIATED_CHECKOUT_ALIASES =
            Set.of("initiated_checkout", "initiatedcheckout", "checkout");
    private static final Set<String> SEARCH_ALIASES = Set.of("search");
    private static final Set<String> ADD_PAYMENT_INFO_ALIASES =
            Set.of("add_payment_info", "addpaymentinfo", "paymentinfo");
    private static final Set<String> FIRST_RECHARGE_ALIASES = Set.of("first_recharge", "firstrecharge");

    private static final Pattern NON_ALNUM = Pattern.compile("[^0-9a-z\\u4e00-\\u9fff]+");
    private static final Pattern SPACES = Pattern.compile("[\\s-]+");

    private CustomEventTypes() {}

    public static String customEventKey(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        String compact = NON_ALNUM.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        String normalized =
                SPACES.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll("_");

        if (text.contains("注册") || COMPLETE_REGISTRATION_ALIASES.contains(compact)) {
            return "complete_registration";
        }
        if (text.contains("线索") || LEAD_ALIASES.contains(compact)) {
            return "lead";
        }
        if (text.contains("购买") || text.contains("购物") || text.contains("下单") || PURCHASE_ALIASES.contains(compact)) {
            return "purchase";
        }
        if (text.contains("加入购物车") || text.contains("加购") || ADD_TO_CART_ALIASES.contains(compact)) {
            return "add_to_cart";
        }
        if (text.contains("发起结账")
                || text.contains("结账")
                || text.contains("结算")
                || INITIATED_CHECKOUT_ALIASES.contains(compact)) {
            return "initiated_checkout";
        }
        if (text.contains("搜索") || SEARCH_ALIASES.contains(compact)) {
            return "search";
        }
        if (text.contains("支付信息") || ADD_PAYMENT_INFO_ALIASES.contains(compact)) {
            return "add_payment_info";
        }
        if (text.contains("首充") || FIRST_RECHARGE_ALIASES.contains(compact)) {
            return "first_recharge";
        }
        return normalized.isEmpty() ? null : normalized;
    }

    public static String customEventType(Object value) {
        String key = customEventKey(value);
        if (key == null) {
            return null;
        }
        if (LEAD_ALIASES.contains(key)) {
            return CUSTOM_EVENT_TYPES.get("complete_registration");
        }
        return CUSTOM_EVENT_TYPES.get(key);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String t = String.valueOf(value).strip();
        return t.isEmpty() ? null : t;
    }
}
