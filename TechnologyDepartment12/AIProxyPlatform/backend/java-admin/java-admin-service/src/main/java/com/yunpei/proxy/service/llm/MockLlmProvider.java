package com.yunpei.proxy.service.llm;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock LLM：返回固定可解析结构，用于 CI/本地打通链路。
 *
 * @author yunpei
 */
public class MockLlmProvider implements LlmProvider {

    public static final String MODEL = "mock-llm";

    private static final Pattern LIMIT_PATTERN = Pattern.compile("\"limit\"\\s*:\\s*(\\d+)");
    private static final Pattern REQUESTED_COUNT_PATTERN = Pattern.compile("\"requested_count\"\\s*:\\s*(\\d+)");
    private static final Pattern PRODUCT_PATTERN = Pattern.compile("\"canonical_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern COPY_PRODUCT_PATTERN = Pattern.compile("\"product_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SELECTED_TOPIC_PATTERN =
            Pattern.compile("\"selected_topic\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CREATIVE_BRIEF_PATTERN =
            Pattern.compile("\"creative_brief\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern DURATION_PATTERN = Pattern.compile("\"duration_seconds\"\\s*:\\s*(\\d+)");
    private static final Pattern ASPECT_PATTERN = Pattern.compile("\"aspect_ratio\"\\s*:\\s*\"([^\"]+)\"");

    private static final String[][] TOPIC_DIRECTIONS = {
        {"Discover a clearer benefit", "聚焦一个清晰的核心价值，降低理解门槛。"},
        {"See a different way to start", "用易于尝试的第一步建立行动意愿。"},
        {"Choose the experience that fits", "从使用体验与选择感切入广告沟通。"},
        {"Make the next action simple", "把产品价值与明确的行动指引连接起来。"},
        {"Explore what makes it useful", "以产品价值探索建立可测试的内容方向。"},
    };

    /** 通用英文广告模板：strategy / primary / headline / description；%s = product display name */
    private static final String[][] COPY_TEMPLATES_GENERAL = {
        {
            "Everyday value",
            "Looking for a simpler way to get more done? Explore practical ways %s can fit your day and help you decide with confidence. Learn more about the options available now.",
            "Discover %s today",
            "See useful features in one place"
        },
        {
            "Simple start",
            "Not sure where to begin? See the key benefits of %s in one clear place so your next step feels easy. Explore your options and start with what fits.",
            "A simpler way to start",
            "Explore options without pressure"
        },
        {
            "Product experience",
            "Want an experience that matches your needs? Find how %s supports your goals with clear benefits and a natural next action. See the details and choose what works.",
            "Find what fits you",
            "Details that help you decide"
        },
        {
            "Clear benefits",
            "Curious about the real upside? Get to know the useful benefits available with %s and understand why people take the next step. Start exploring today.",
            "Explore the benefits",
            "Clear benefits, simple next step"
        },
        {
            "Direct action",
            "Ready to move forward? Learn about %s and pick the option that fits your needs with a straightforward path to act. Take the next step with confidence.",
            "Take the next step",
            "A clear path to act now"
        },
    };

    /**
     * 体重目标 + 私信/对话承接的英文广告模板（对齐常见 WhatsApp / MESSAGE_PAGE 工单）。
     * primary 可含 %s 品名；headline/description 偏利益与行动，避免堆产品名。
     */
    private static final String[][] COPY_TEMPLATES_WEIGHT_MESSAGING = {
        {
            "Weight goal plan",
            "Building better habits starts with a plan that fits real life. Message us to explore simple weight-goal options with %s—clear information, no pressure. Start a chat when you're ready.",
            "Plan your weight goals",
            "Chat for a simple starting plan"
        },
        {
            "Gentle consistency",
            "Want steadier routines without the overwhelm? See how %s helps you understand your options and stay consistent day to day. Message a guide and take one small step.",
            "Stay consistent daily",
            "Gentle tips, clear next step"
        },
        {
            "Conversation first",
            "Not sure where to begin with weight goals? Start with one message—ask questions, get clearer information, and choose what fits with %s. We're here to help you decide.",
            "Start with one message",
            "Ask questions before you commit"
        },
        {
            "Clearer choices",
            "Looking for clearer information before you decide? Talk with a guide about practical options around %s and find a path that matches your routine. Message us anytime.",
            "Get clearer options",
            "Helpful answers in one chat"
        },
        {
            "Low-pressure start",
            "A better plan can start with a short conversation. Explore %s with a friendly guide, learn what fits your goals, and move forward only when it feels right.",
            "A low-pressure first step",
            "Learn first, then decide"
        },
    };

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (request == null
                || request.userPrompt() == null
                || request.userPrompt().isBlank()) {
            throw new LlmProviderException("userPrompt is required");
        }
        String purpose =
                request.purpose() == null ? "generic" : request.purpose().trim();
        String content =
                switch (purpose) {
                    case "topics" -> topicsJson(request.userPrompt());
                    case "copy" -> copyJson(request.userPrompt());
                    case "storyboard" -> storyboardJson(request.userPrompt());
                    case "work_order_delivery_fields" -> "{\"fields\":{}}";
                    case "work_order_analysis_hints" -> workOrderAnalysisHints(request.userPrompt());
                    case "work_order_analysis" ->
                        "{\"campaign\":{\"objective\":\"OUTCOME_SALES\"},"
                                + "\"adset\":{\"optimization_goal\":\"OFFSITE_CONVERSIONS\"},"
                                + "\"creative\":{\"name\":\"Mock Creative\"},"
                                + "\"review\":{\"notes\":\"mock rule-first skeleton\"}}";
                    default -> "{\"content\":\"mock-llm-output\",\"purpose\":\"" + purpose + "\"}";
                };
        return LlmResponse.of(content, MODEL);
    }

    /**
     * 稳定分镜文案：格式行含 duration/aspect，对齐 Python mock 语义。
     */
    private static String storyboardJson(String userPrompt) {
        int duration = 12;
        Matcher durationMatcher = DURATION_PATTERN.matcher(userPrompt == null ? "" : userPrompt);
        if (durationMatcher.find()) {
            try {
                duration = Integer.parseInt(durationMatcher.group(1));
            } catch (NumberFormatException ignore) {
                duration = 12;
            }
        }
        String aspect = "9:16";
        Matcher aspectMatcher = ASPECT_PATTERN.matcher(userPrompt == null ? "" : userPrompt);
        if (aspectMatcher.find()) {
            aspect = aspectMatcher.group(1);
        }
        String text = "Format: "
                + duration
                + " seconds, "
                + aspect
                + ", one continuous clip.\\n"
                + "Opening: Preserve the opening identity and establish motion.\\n"
                + "Middle: Execute one causal action arc with smooth camera response.\\n"
                + "Ending: Recover and settle into the required ending state.";
        return "{\"storyboard_text\":\"" + text + "\"}";
    }

    /**
     * 稳定 copy variants：含 strategy_summary/primary_text/headline/description；条数尊重 requested_count。
     *
     * <p>按 brief 主题切换模板；product_name 视为已本地化的展示名（不应再含突兀汉字）。
     */
    private static String copyJson(String userPrompt) {
        int count = 1;
        Matcher countMatcher = REQUESTED_COUNT_PATTERN.matcher(userPrompt);
        if (countMatcher.find()) {
            try {
                count = Integer.parseInt(countMatcher.group(1));
            } catch (NumberFormatException ignore) {
                count = 1;
            }
        }
        if (count < 1) {
            count = 1;
        }
        if (count > 5) {
            count = 5;
        }
        String product = "Product";
        Matcher productMatcher = COPY_PRODUCT_PATTERN.matcher(userPrompt);
        if (productMatcher.find()) {
            product = productMatcher.group(1);
        }
        // 防御：mock 侧也不把汉字写进英文广告字段
        if (containsCjk(product)) {
            product = "this offer";
        }
        String[][] templates = chooseCopyTemplates(userPrompt);
        StringBuilder sb = new StringBuilder("{\"variants\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String[] t = templates[i % templates.length];
            sb.append("{\"strategy_summary\":\"")
                    .append(escapeJson(t[0]))
                    .append("\",\"primary_text\":\"")
                    .append(escapeJson(String.format(t[1], product)))
                    .append("\",\"headline\":\"")
                    .append(escapeJson(formatOptional(t[2], product)))
                    .append("\",\"description\":\"")
                    .append(escapeJson(formatOptional(t[3], product)))
                    .append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String[][] chooseCopyTemplates(String userPrompt) {
        String topic = extractSelectedTopic(userPrompt);
        String haystack = ((userPrompt == null ? "" : userPrompt) + " " + topic).toLowerCase(Locale.ROOT);
        boolean weight = haystack.contains("体重")
                || haystack.contains("weight")
                || haystack.contains("diet")
                || haystack.contains("健康生活")
                || haystack.contains("slim");
        boolean messaging = haystack.contains("whatsapp")
                || haystack.contains("私信")
                || haystack.contains("对话")
                || haystack.contains("message")
                || haystack.contains("messaging")
                || haystack.contains("客服")
                || haystack.contains("顾问");
        if (weight || messaging) {
            return COPY_TEMPLATES_WEIGHT_MESSAGING;
        }
        return COPY_TEMPLATES_GENERAL;
    }

    private static String extractSelectedTopic(String userPrompt) {
        if (userPrompt == null) {
            return "";
        }
        Matcher brief = CREATIVE_BRIEF_PATTERN.matcher(userPrompt);
        if (brief.find()) {
            return brief.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        Matcher m = SELECTED_TOPIC_PATTERN.matcher(userPrompt);
        if (!m.find()) {
            return "";
        }
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String formatOptional(String template, String product) {
        if (template == null) {
            return "";
        }
        if (template.contains("%s")) {
            return String.format(template, product);
        }
        return template;
    }

    private static boolean containsCjk(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if ((cp >= 0x4e00 && cp <= 0x9fff) || (cp >= 0x3400 && cp <= 0x4dbf) || (cp >= 0xf900 && cp <= 0xfaff)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * 稳定 topics 结构：含 title/angle/audience/selling_points/rationale；条数尊重 user JSON 的 limit。
     */
    private static String topicsJson(String userPrompt) {
        int limit = 3;
        Matcher limitMatcher = LIMIT_PATTERN.matcher(userPrompt);
        if (limitMatcher.find()) {
            try {
                limit = Integer.parseInt(limitMatcher.group(1));
            } catch (NumberFormatException ignore) {
                limit = 3;
            }
        }
        if (limit < 1) {
            limit = 1;
        }
        if (limit > 5) {
            limit = 5;
        }
        String product = "Product";
        Matcher productMatcher = PRODUCT_PATTERN.matcher(userPrompt);
        if (productMatcher.find()) {
            product = productMatcher.group(1);
        }
        StringBuilder sb = new StringBuilder("{\"topics\":[");
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String[] dir = TOPIC_DIRECTIONS[i % TOPIC_DIRECTIONS.length];
            sb.append("{\"title\":\"")
                    .append(escapeJson(product))
                    .append(": ")
                    .append(escapeJson(dir[0]))
                    .append("\",\"angle\":\"")
                    .append(escapeJson(dir[1]))
                    .append("\",\"audience\":\"面向目标市场的潜在兴趣用户，结合现有投放设置持续测试与优化。\"")
                    .append(",\"selling_points\":[\"围绕产品核心价值建立清晰、可测试的广告沟通。\"]")
                    .append(",\"rationale\":\"将产品价值与当前投放目标结合，便于在 Facebook 广告中测试用户反馈。\"")
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 确定性 mock：仅在规则可能漏检的长尾措辞下给出补洞提示（对齐 Python mock hints 语义）。
     */
    private static String workOrderAnalysisHints(String userPrompt) {
        String text = userPrompt == null ? "" : userPrompt;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("wts") || lower.contains("whatsapp") || lower.contains("whats app") || text.contains("瓦")) {
            return "{\"objective\":\"OUTCOME_ENGAGEMENT\",\"confidence\":0.9,"
                    + "\"matched_value\":\"WhatsApp\",\"evidence\":\"messaging\","
                    + "\"messaging\":{\"channel\":\"WHATSAPP\",\"whatsAppAreaCode\":\"\","
                    + "\"whatsAppNumber\":\"\"}}";
        }
        if (lower.contains("play.google") || lower.contains("apps.apple") || text.contains("应用")) {
            return "{\"objective\":\"OUTCOME_APP_PROMOTION\",\"confidence\":0.9,"
                    + "\"matched_value\":\"App\",\"evidence\":\"app store\"}";
        }
        if (lower.contains("purchase") || text.contains("购买") || lower.contains("roas")) {
            return "{\"objective\":\"OUTCOME_SALES\",\"confidence\":0.85,"
                    + "\"matched_value\":\"conversion\",\"evidence\":\"sales event\"}";
        }
        return "{\"objective\":\"OUTCOME_TRAFFIC\",\"confidence\":0.6,"
                + "\"matched_value\":\"traffic\",\"evidence\":\"fallback\"}";
    }
}
