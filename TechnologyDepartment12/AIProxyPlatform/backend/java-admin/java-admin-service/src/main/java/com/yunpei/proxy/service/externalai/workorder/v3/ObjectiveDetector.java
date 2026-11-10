package com.yunpei.proxy.service.externalai.workorder.v3;

import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_APP_PROMOTION;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_ENGAGEMENT;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_SALES;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.OBJECTIVE_TRAFFIC;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_APP;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_CONVERSION;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_FALLBACK;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_LLM_FALLBACK;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_SOCIAL;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_TRAFFIC;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则优先广告目标检测（对齐 Python {@code objective_detector}）。
 *
 * @author yunpei
 */
public final class ObjectiveDetector {

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHATSAPP = Pattern.compile(
            "whats\\s*app|watch\\s*app|瓦次?app|(?<![a-z0-9])ws(?![a-z0-9])|(?<![a-z0-9])wa(?![a-z0-9])",
            Pattern.CASE_INSENSITIVE);

    private static final List<LabeledPattern> MESSAGING_PATTERNS = List.of(
            new LabeledPattern("WhatsApp", WHATSAPP),
            new LabeledPattern(
                    "Instagram",
                    Pattern.compile("instagram|ins\\s*私信|ig\\s*私信|instagram\\s*direct", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "Facebook公共主页",
                    Pattern.compile(
                            "facebook\\s*公共主页|fb\\s*公共主页|公共主页|messenger|"
                                    + "脸书\\s*主页|facebook\\s*page|fan\\s*page|"
                                    + "主页\\s*(消息|私信|对话)|page\\s*message",
                            Pattern.CASE_INSENSITIVE)));

    private static final List<LabeledPattern> APP_URL_PATTERNS = List.of(
            new LabeledPattern(
                    "Google Play",
                    Pattern.compile("play\\.google\\.com|google\\s*play|安卓应用商店", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "App Store",
                    Pattern.compile(
                            "apps\\.apple\\.com|itunes\\.apple\\.com|app\\s*store|苹果商店", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "应用地址",
                    Pattern.compile(
                            "应用(商店)?(地址|链接|下载)|object_store_url|应用包名|package\\s*name", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("应用标识", Pattern.compile("\\b[a-zA-Z][\\w]*\\.[a-zA-Z][\\w]*(?:\\.[\\w]+)+\\b")));

    private static final List<LabeledPattern> TRAFFIC_EVENT_PATTERNS = List.of(
            new LabeledPattern(
                    "无事件", Pattern.compile("无事件|没有事件|不设(置)?事件|no\\s*event|\\bnone\\b", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "落地页",
                    Pattern.compile("落地页(访问|浏览)?|landing\\s*page\\s*views?|\\blpv\\b", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "浏览", Pattern.compile("(内容)?浏览|content\\s*view|page\\s*view|浏览量", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("访问", Pattern.compile("访问|访客|\\bvisit\\b", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "点击", Pattern.compile("链接点击|点击|link\\s*clicks?|\\bclicks?\\b", Pattern.CASE_INSENSITIVE)));

    private static final List<LabeledPattern> SALES_EVENT_PATTERNS = List.of(
            new LabeledPattern("PURCHASE", Pattern.compile("purchase|购买|下单|成交|付费转化|购物", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "ADD_TO_CART", Pattern.compile("add[_\\s-]?to[_\\s-]?cart|加购|加入购物车", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("CHECKOUT", Pattern.compile("checkout|结账|结算|发起结账", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("SUBSCRIBE", Pattern.compile("subscribe|订阅", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "REGISTER",
                    Pattern.compile(
                            "(?<![a-z_])register(?![a-z_])|注册|signup|sign[_\\s-]?up|" + "complete[_\\s-]?registration",
                            Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("LEAD", Pattern.compile("\\blead\\b|线索|留资", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("ROAS", Pattern.compile("\\broas\\b|价值优化|广告支出回报", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern("首充", Pattern.compile("首充|first[_\\s-]?recharge", Pattern.CASE_INSENSITIVE)),
            new LabeledPattern(
                    "ADD_PAYMENT_INFO", Pattern.compile("支付信息|add[_\\s-]?payment", Pattern.CASE_INSENSITIVE)));

    private static final Pattern EVENT_LINE =
            Pattern.compile("(?:投放事件|转化事件|优化事件|事件|event)\\s*[:：]\\s*([^\\n|;；]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPTY_EVENT = Pattern.compile("[-—无]?|n/?a|null|none", Pattern.CASE_INSENSITIVE);

    private static final String MESSAGE_PLATFORM_WHATSAPP = "whatsApp";
    private static final String MESSAGE_PLATFORM_INSTAGRAM = "instagram";
    private static final String MESSAGE_PLATFORM_MESSAGE = "message";

    private static final Pattern WA_PHONE_NEAR = Pattern.compile(
            "(?:whats\\s*app|watch\\s*app|瓦次?app|(?<![a-z0-9])ws(?![a-z0-9])|(?<![a-z0-9])wa(?![a-z0-9]))"
                    + "[\\s:：\\-]*"
                    + "(?:\\+?\\s*(\\d{1,4})\\s*[-–—]?\\s*)?"
                    + "([0-9][0-9\\s\\-()]{5,20}\\d)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WA_AREA_LABEL = Pattern.compile(
            "(?:whats\\s*app|watch\\s*app|瓦次?app|(?<![a-z0-9])ws(?![a-z0-9])|(?<![a-z0-9])wa(?![a-z0-9]))?"
                    + "[\\s\\S]{0,40}?"
                    + "(?:区号|国家码|country\\s*code|area\\s*code)\\s*[:：]?\\s*\\+?(\\d{1,4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WA_NUMBER_LABEL = Pattern.compile(
            "(?:whats\\s*app|watch\\s*app|瓦次?app|(?<![a-z0-9])ws(?![a-z0-9])|(?<![a-z0-9])wa(?![a-z0-9]))?"
                    + "[\\s\\S]{0,40}?"
                    + "(?:号码|手机号|电话|phone|number)\\s*[:：]?\\s*\\+?([0-9][0-9\\s\\-()]{5,20}\\d)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern E164_PHONE = Pattern.compile("\\+(\\d{1,4})[\\s\\-]?([0-9][0-9\\s\\-()]{5,18}\\d)");

    private static final Set<String> VALID_OBJECTIVES =
            Set.of(OBJECTIVE_ENGAGEMENT, OBJECTIVE_APP_PROMOTION, OBJECTIVE_SALES, OBJECTIVE_TRAFFIC);

    private static final Set<String> VALID_MESSAGING_CHANNELS = Set.of("WHATSAPP", "INSTAGRAM_DIRECT", "MESSENGER");

    private static final double LLM_OBJECTIVE_MIN_CONFIDENCE = 0.55;

    private ObjectiveDetector() {}

    public static ObjectiveDetection detectObjective(String workOrderText) {
        String text = workOrderText == null ? "" : workOrderText;
        String textNoUrl = stripUrls(text);

        for (LabeledPattern item : MESSAGING_PATTERNS) {
            if (item.pattern().matcher(textNoUrl).find()
                    || item.pattern().matcher(text).find()) {
                return new ObjectiveDetection(OBJECTIVE_ENGAGEMENT, RULE_SOCIAL, item.label());
            }
        }

        for (LabeledPattern item : APP_URL_PATTERNS) {
            Matcher match = item.pattern().matcher(text);
            if (!match.find()) {
                continue;
            }
            if ("应用标识".equals(item.label())
                    && !Pattern.compile("应用|app|install|安装|下载|包名|package", Pattern.CASE_INSENSITIVE)
                            .matcher(textNoUrl)
                            .find()) {
                continue;
            }
            String value = "应用标识".equals(item.label()) ? match.group(0) : item.label();
            return new ObjectiveDetection(OBJECTIVE_APP_PROMOTION, RULE_APP, value);
        }

        Matcher eventLineMatch = EVENT_LINE.matcher(textNoUrl);
        if (eventLineMatch.find()) {
            String line = stripUrls(eventLineMatch.group(1));
            String lineTraffic = firstMatch(TRAFFIC_EVENT_PATTERNS, line);
            String lineSales = firstMatch(SALES_EVENT_PATTERNS, line);
            if (lineTraffic != null && lineSales == null) {
                return new ObjectiveDetection(OBJECTIVE_TRAFFIC, RULE_TRAFFIC, lineTraffic);
            }
            if (lineSales != null) {
                return new ObjectiveDetection(OBJECTIVE_SALES, RULE_CONVERSION, lineSales);
            }
            String stripped = line.strip();
            if (stripped.isEmpty() || EMPTY_EVENT.matcher(stripped).matches()) {
                return new ObjectiveDetection(OBJECTIVE_TRAFFIC, RULE_TRAFFIC, "无事件");
            }
        }

        String trafficHit = firstMatch(TRAFFIC_EVENT_PATTERNS, textNoUrl);
        String salesHit = firstMatch(SALES_EVENT_PATTERNS, textNoUrl);
        if (trafficHit != null && salesHit == null) {
            return new ObjectiveDetection(OBJECTIVE_TRAFFIC, RULE_TRAFFIC, trafficHit);
        }
        if (salesHit != null) {
            return new ObjectiveDetection(OBJECTIVE_SALES, RULE_CONVERSION, salesHit);
        }
        if (trafficHit != null) {
            return new ObjectiveDetection(OBJECTIVE_TRAFFIC, RULE_TRAFFIC, trafficHit);
        }
        return new ObjectiveDetection(OBJECTIVE_TRAFFIC, RULE_FALLBACK, "无法识别");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> messagingChannelMapping(String workOrderText, Map<String, Object> llmHints) {
        String text = workOrderText == null ? "" : workOrderText;
        java.util.ArrayList<String> channels = new java.util.ArrayList<>();
        boolean hasWhatsapp = WHATSAPP.matcher(text).find();
        boolean hasInstagram = Pattern.compile(
                        "instagram|ins\\s*私信|ig\\s*私信|instagram\\s*direct", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
        boolean hasMessenger = Pattern.compile(
                        "facebook\\s*公共主页|fb\\s*公共主页|公共主页|messenger|facebook\\s*page|fan\\s*page|"
                                + "主页\\s*(消息|私信|对话)|page\\s*message",
                        Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
        if (hasWhatsapp) {
            channels.add("WHATSAPP");
        }
        if (hasInstagram) {
            channels.add("INSTAGRAM_DIRECT");
        }
        if (hasMessenger) {
            channels.add("MESSENGER");
        }

        Map<String, Object> hints = llmHints == null ? Map.of() : llmHints;
        Object messagingObj = hints.get("messaging");
        Map<String, Object> messaging = messagingObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        if (channels.isEmpty()) {
            String hintChannel = String.valueOf(
                            messaging.getOrDefault("channel", messaging.getOrDefault("destination_type", "")))
                    .toUpperCase(Locale.ROOT);
            if (VALID_MESSAGING_CHANNELS.contains(hintChannel)) {
                channels.add(hintChannel);
                if ("WHATSAPP".equals(hintChannel)) {
                    hasWhatsapp = true;
                } else if ("INSTAGRAM_DIRECT".equals(hintChannel)) {
                    hasInstagram = true;
                } else {
                    hasMessenger = true;
                }
            }
        }
        if (channels.isEmpty()) {
            channels.add("MESSENGER");
        }

        String primary = channels.get(0);
        String destinationType =
                switch (primary) {
                    case "WHATSAPP" -> "WHATSAPP";
                    case "INSTAGRAM_DIRECT" -> "INSTAGRAM_DIRECT";
                    default -> "MESSENGER";
                };

        List<String> messagePlatform;
        if (hasWhatsapp || "WHATSAPP".equals(primary)) {
            messagePlatform = List.of(MESSAGE_PLATFORM_WHATSAPP);
        } else if (hasInstagram || "INSTAGRAM_DIRECT".equals(primary)) {
            messagePlatform = List.of(MESSAGE_PLATFORM_INSTAGRAM);
        } else {
            messagePlatform = List.of(MESSAGE_PLATFORM_MESSAGE);
        }

        String areaCode = "";
        String number = "";
        if (hasWhatsapp || "WHATSAPP".equals(primary)) {
            String[] phone = extractWhatsappPhone(text);
            areaCode = phone[0];
            number = phone[1];
            if (areaCode.isEmpty()) {
                areaCode = digitsOnly(String.valueOf(messaging.getOrDefault("whatsAppAreaCode", "")));
            }
            if (number.isEmpty()) {
                number = digitsOnly(String.valueOf(messaging.getOrDefault("whatsAppNumber", "")));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messaging_channels", channels);
        result.put("destination_type", destinationType);
        result.put("messagePlatform", messagePlatform);
        result.put("whatsAppAreaCode", areaCode);
        result.put("whatsAppNumber", number);
        return result;
    }

    public static ObjectiveDetection applyLlmObjectiveHints(
            ObjectiveDetection detection, Map<String, Object> llmHints) {
        if (!RULE_FALLBACK.equals(detection.matchedRule())) {
            return detection;
        }
        if (llmHints == null || llmHints.isEmpty()) {
            return detection;
        }
        double confidence = 0.0;
        try {
            Object conf = llmHints.get("confidence");
            if (conf != null) {
                confidence = Double.parseDouble(String.valueOf(conf));
            }
        } catch (NumberFormatException ignore) {
            confidence = 0.0;
        }
        if (confidence < LLM_OBJECTIVE_MIN_CONFIDENCE) {
            return detection;
        }
        String objective =
                String.valueOf(llmHints.getOrDefault("objective", "")).strip().toUpperCase(Locale.ROOT);
        objective = switch (objective) {
            case "ENGAGEMENT", "OUTCOME_ENGAGEMENT" -> OBJECTIVE_ENGAGEMENT;
            case "APP", "APP_PROMOTION", "OUTCOME_APP_PROMOTION" -> OBJECTIVE_APP_PROMOTION;
            case "SALES", "OUTCOME_SALES" -> OBJECTIVE_SALES;
            case "TRAFFIC", "OUTCOME_TRAFFIC" -> OBJECTIVE_TRAFFIC;
            default -> objective;
        };
        if (!VALID_OBJECTIVES.contains(objective)) {
            return detection;
        }
        String matchedValue = String.valueOf(
                        llmHints.getOrDefault("matched_value", llmHints.getOrDefault("evidence", "llm")))
                .strip();
        if (matchedValue.isEmpty() || "null".equals(matchedValue)) {
            matchedValue = "llm";
        }
        if (matchedValue.length() > 80) {
            matchedValue = matchedValue.substring(0, 80);
        }
        return new ObjectiveDetection(objective, RULE_LLM_FALLBACK, matchedValue);
    }

    public static boolean needsV3LlmFallback(
            String workOrderText, ObjectiveDetection detection, Integer runStartTime, Integer runStopTime) {
        String text = workOrderText == null ? "" : workOrderText;
        if (RULE_FALLBACK.equals(detection.matchedRule())) {
            return true;
        }
        if (runStartTime == null || runStopTime == null) {
            if (Pattern.compile("投放时间|投放时段|投放时间段|运行时间|daypart", Pattern.CASE_INSENSITIVE)
                    .matcher(text)
                    .find()) {
                if (!Pattern.compile("立即|立刻|马上|全天|不限时间|24\\s*小时", Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find()) {
                    return true;
                }
            }
        }
        if (!OBJECTIVE_ENGAGEMENT.equals(detection.detectedObjective())) {
            if (WHATSAPP.matcher(text).find()
                    || Pattern.compile(
                                    "(?:whats\\s*app|watch\\s*app|瓦次?app|(?<![a-z0-9])ws(?![a-z0-9]))"
                                            + "\\s*[:：]\\s*\\+?\\d",
                                    Pattern.CASE_INSENSITIVE)
                            .matcher(text)
                            .find()) {
                return true;
            }
        }
        return false;
    }

    private static String[] extractWhatsappPhone(String text) {
        String area = "";
        String number = "";
        Matcher labelArea = WA_AREA_LABEL.matcher(text);
        if (labelArea.find()) {
            area = labelArea.group(1);
        }
        Matcher labelNumber = WA_NUMBER_LABEL.matcher(text);
        if (labelNumber.find()) {
            number = digitsOnly(labelNumber.group(1));
        }
        if (number.isEmpty()) {
            Matcher near = WA_PHONE_NEAR.matcher(text);
            if (near.find()) {
                if (near.group(1) != null) {
                    area = area.isEmpty() ? near.group(1) : area;
                }
                number = digitsOnly(near.group(2));
            }
        }
        if (number.isEmpty()) {
            Matcher e164 = E164_PHONE.matcher(text);
            while (e164.find()) {
                int windowStart = Math.max(0, e164.start() - 40);
                String window = text.substring(windowStart, Math.min(text.length(), e164.end() + 10));
                if (WHATSAPP.matcher(window).find()
                        || Pattern.compile("电话|手机|号码").matcher(window).find()) {
                    area = area.isEmpty() ? e164.group(1) : area;
                    number = digitsOnly(e164.group(2));
                    break;
                }
            }
        }
        return new String[] {area, number};
    }

    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    private static String stripUrls(String text) {
        return URL.matcher(text).replaceAll(" ");
    }

    private static String firstMatch(List<LabeledPattern> patterns, String text) {
        for (LabeledPattern item : patterns) {
            if (item.pattern().matcher(text).find()) {
                return item.label();
            }
        }
        return null;
    }

    private record LabeledPattern(String label, Pattern pattern) {}
}
