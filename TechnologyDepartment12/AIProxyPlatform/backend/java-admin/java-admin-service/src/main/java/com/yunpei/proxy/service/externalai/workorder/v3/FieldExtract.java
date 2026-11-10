package com.yunpei.proxy.service.externalai.workorder.v3;

import com.yunpei.proxy.service.externalai.workorder.CountryCodes;
import com.yunpei.proxy.service.externalai.workorder.CustomEventTypes;
import com.yunpei.proxy.service.externalai.workorder.WorkOrderParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合并工单结构化解析与 LLM 投递字段（对齐 Python {@code field_extract}）。
 *
 * @author yunpei
 */
public final class FieldExtract {

    private static final Pattern URL = Pattern.compile("https?://[^\\s\\]\\)\"'，。；、；,]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE_RANGE =
            Pattern.compile("(?:年龄|age)\\s*[:：]?\\s*(\\d{1,2})\\s*[-~到至]\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE_MIN =
            Pattern.compile("(?:最小年龄|age[_\\s-]?min)\\s*[:：]?\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE_MAX =
            Pattern.compile("(?:最大年龄|age[_\\s-]?max)\\s*[:：]?\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET =
            Pattern.compile("(?:日预算|daily\\s*budget|预算)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BID_AMOUNT = Pattern.compile(
            "(?:出价上限|bid\\s*(?:cap|amount)|上限)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT = Pattern.compile(
            "(?:项目名称|产品名称|品牌名称|产品|品牌|project\\s*name|product\\s*name|brand)\\s*[:：]\\s*([^\\n|;；]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BRAND_PREFIX = Pattern.compile("^(?:[A-Za-z]+\\s*[-－_]\\s*)+");
    private static final Pattern BRAND_TRAILING_PERCENTAGES = Pattern.compile("(?:\\d+(?:\\.\\d+)?\\s*[%％]\\s*)+$");
    private static final Pattern BRAND_TRAILING_PERCENTAGE_RANGE =
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*[%％]\\s*[-–—~至到]\\s*" + "\\d+(?:\\.\\d+)?\\s*[%％]\\s*$");
    private static final Pattern BRAND_VALUE_END = Pattern.compile("[\\r\\n|;；。]");
    private static final String DAYPART_WORD = "(?:上午|下午|中午|晚上|晚间|凌晨|早上|早晨|傍晚)?";
    private static final Pattern RUN_HOURS_LABELED = Pattern.compile(
            "(?:投放时间|投放时段|投放时间段|投放小时|投放排期|投放时间范围|"
                    + "投放时间窗|运行时间|run\\s*time|daypart|day\\s*part)\\s*[:：]?\\s*"
                    + DAYPART_WORD
                    + "\\s*"
                    + "(\\d{1,2})(?:\\s*[:：点时]\\s*\\d{0,2})?\\s*"
                    + "(?:[-~到至—–]|至)\\s*"
                    + DAYPART_WORD
                    + "\\s*"
                    + "(\\d{1,2})(?:\\s*[:：点时]\\s*\\d{0,2})?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN_HOURS_BARE = Pattern.compile(
            DAYPART_WORD
                    + "\\s*"
                    + "(\\d{1,2})\\s*(?:点|时|:|：)\\s*(?:\\d{2})?\\s*"
                    + "(?:[-~到至—–]|至)\\s*"
                    + DAYPART_WORD
                    + "\\s*"
                    + "(\\d{1,2})\\s*(?:点|时|:|：)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN_HOURS_IMMEDIATE =
            Pattern.compile("立即|立刻|马上|全天|不限时间|24\\s*小时", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOURS_FRAGMENT = Pattern.compile(
            DAYPART_WORD
                    + "\\s*"
                    + "(\\d{1,2})(?:\\s*[:：点时]\\s*\\d{0,2})?\\s*"
                    + "(?:[-~到至—–]|至)\\s*"
                    + DAYPART_WORD
                    + "\\s*"
                    + "(\\d{1,2})(?:\\s*[:：点时]\\s*\\d{0,2})?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_FULL =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}(?::\\d{2})?(?:Z|[+-]\\d{2}:?\\d{2})?");

    private FieldExtract() {}

    public static ExtractedFields mergeExtractedFields(
            String workOrderText, Map<String, Object> llmFields, String media) {
        String text = workOrderText == null ? "" : workOrderText;
        Map<String, Object> structured = WorkOrderParser.parseWorkOrderText(text);
        Map<String, Object> llm = llmFields == null ? Map.of() : llmFields;
        ExtractedFields result = new ExtractedFields();

        result.setProjectName(firstText(
                matchGroup(PROJECT, text),
                structured.get("project_name"),
                structured.get("product_name"),
                fieldValue(llm, "project_name"),
                fieldValue(llm, "product_name")));
        if (result.getProjectName() != null) {
            result.getSources().put("project_name", "work_order");
        }

        BrandResolved displayName = resolveDisplayName(structured, llm);
        result.setProductName(displayName.name());
        result.setBrandName(displayName.name());
        if (displayName.name() != null && displayName.source() != null) {
            result.getSources().put("product_name", displayName.source());
            result.getSources().put("brand_name", displayName.source());
        }

        result.setLandingUrl(
                firstText(structured.get("landing_url"), fieldValue(llm, "landing_url"), firstNonAppUrl(text)));
        if (result.getLandingUrl() != null) {
            result.getSources().put("landing_url", "work_order");
        }

        result.setEventName(firstText(
                structured.get("event_name"),
                fieldValue(llm, "event_name"),
                fieldValue(llm, "event"),
                findEventLabel(text)));
        if (result.getEventName() != null) {
            String mapped = CustomEventTypes.customEventType(result.getEventName());
            result.setCustomEventType(mapped != null ? mapped : mapEventToken(result.getEventName()));
            result.getSources().put("event_name", "work_order");
        }

        Object countryRaw =
                firstValue(structured.get("country"), fieldValue(llm, "country"), fieldValue(llm, "countries"));
        result.setCountries(
                countryRaw != null
                        ? CountryCodes.parseCountryCodes(countryRaw)
                        : CountryCodes.findDeliveryCountryCodes(text));
        if (!result.getCountries().isEmpty()) {
            result.getSources().put("countries", "work_order");
        }

        int[] ages = parseAges(text, structured, llm);
        result.setAgeMin(ages[0] < 0 ? null : ages[0]);
        result.setAgeMax(ages[1] < 0 ? null : ages[1]);
        if (result.getAgeMin() != null) {
            result.getSources().put("age", "work_order");
        }

        String gender = firstText(structured.get("gender"), fieldValue(llm, "gender"), inferGender(text));
        result.setGender(normalizeGender(gender));
        if (result.getGender() != null) {
            result.getSources().put("gender", "work_order");
        }

        result.setAudience(firstText(
                structured.get("audience_description"),
                fieldValue(llm, "audience_description_raw"),
                fieldValue(llm, "audience")));

        result.setDailyBudget(parseFloat(structured.get("payout_amount"), matchGroup(BUDGET, text)));
        if (result.getDailyBudget() != null) {
            result.getSources().put("daily_budget", "work_order");
        }

        if (Pattern.compile("广告组\\s*(日)?预算|ad\\s*set\\s*(daily\\s*)?budget|\\bABO\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            result.setBudgetType("adset");
        } else {
            // 广告系列 / CBO / 默认均为 campaign 预算
            result.setBudgetType("campaign");
        }
        boolean budgetTyped = Pattern.compile("广告组|广告系列|ABO|CBO|campaign|ad\\s*set", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
        result.getSources().put("budget_type", budgetTyped ? "work_order" : "default");

        result.setBidWantsCap(
                Pattern.compile("出价上限|有上限|bid\\s*cap|with\\s*bid\\s*cap|设(置)?上限", Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find());
        if (Pattern.compile("最低成本\\s*(无|不设|不设置)?\\s*(出价)?上限|without\\s*(a\\s*)?cap|无上限", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            result.setBidWantsCap(false);
        }
        result.setBidAmount(parseFloat(matchGroup(BID_AMOUNT, text)));
        if (result.getBidAmount() != null) {
            result.setBidWantsCap(true);
            result.getSources().put("bid_amount", "work_order");
        }

        if (Pattern.compile("视频|video", Pattern.CASE_INSENSITIVE).matcher(text).find()
                && !Pattern.compile("图片|image", Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find()) {
            result.setCreativeType("video");
        } else {
            result.setCreativeType("image");
        }

        if (Pattern.compile("仅移动|移动端|mobile\\s*only|仅\\s*mobile", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            result.setDevicePlatforms(List.of("mobile"));
        } else if (Pattern.compile("桌面|desktop", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            result.setDevicePlatforms(List.of("desktop"));
        }

        if (Pattern.compile("自动版位|automatic\\s*placement", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            result.setPlacementMode("automatic");
        } else if (Pattern.compile("手动版位|manual\\s*placement", Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find()
                || (Pattern.compile("facebook", Pattern.CASE_INSENSITIVE)
                                .matcher(text)
                                .find()
                        && Pattern.compile("instagram", Pattern.CASE_INSENSITIVE)
                                .matcher(text)
                                .find()
                        && Pattern.compile("版位").matcher(text).find())) {
            result.setPlacementMode("manual");
            List<String> pubs = new ArrayList<>();
            if (Pattern.compile("facebook", Pattern.CASE_INSENSITIVE)
                    .matcher(text)
                    .find()) {
                pubs.add("facebook");
            }
            if (Pattern.compile("instagram", Pattern.CASE_INSENSITIVE)
                    .matcher(text)
                    .find()) {
                pubs.add("instagram");
            }
            result.setPublisherPlatforms(pubs.isEmpty() ? List.of("facebook", "instagram") : pubs);
        }

        if (Pattern.compile("立即开始|立刻开始|立即投放", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            result.setStartType("immediate");
        }
        Matcher schedule = Pattern.compile(
                        "(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}(?::\\d{2})?(?:Z|[+-]\\d{2}:?\\d{2})?)")
                .matcher(text);
        if (schedule.find()) {
            result.setStartType("scheduled");
            result.setStartTime(normalizeIso(schedule.group(1)));
            String rest = text.substring(schedule.end());
            Matcher endM = Pattern.compile(
                            "(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}(?::\\d{2})?(?:Z|[+-]\\d{2}:?\\d{2})?)")
                    .matcher(rest);
            if (endM.find()) {
                result.setEndTime(normalizeIso(endM.group(1)));
            }
        }

        Integer[] runHours = parseRunHours(
                text,
                structured.get("launch_time"),
                fieldValue(llm, "launch_time"),
                fieldValue(llm, "run_start_time"),
                fieldValue(llm, "run_stop_time"));
        result.setRunStartTime(runHours[0]);
        result.setRunStopTime(runHours[1]);
        if (runHours[0] != null && runHours[1] != null) {
            result.getSources().put("run_hours", "work_order");
        }

        String appUrl = firstAppStoreUrl(text);
        if (appUrl != null) {
            result.setObjectStoreUrl(appUrl);
            result.setMobileAppStore(storeFromUrl(appUrl));
            result.getSources().put("object_store_url", "work_order");
        }

        Matcher attr = Pattern.compile(
                        "(\\d+)\\s*天点击.*?(\\d+)\\s*天浏览|(\\d+)\\s*day\\s*click.*?(\\d+)\\s*day\\s*view",
                        Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (attr.find()) {
            if (attr.group(1) != null) {
                result.setAttributionClickDays(Integer.parseInt(attr.group(1)));
                result.setAttributionViewDays(Integer.parseInt(attr.group(2)));
            } else {
                result.setAttributionClickDays(Integer.parseInt(attr.group(3)));
                result.setAttributionViewDays(Integer.parseInt(attr.group(4)));
            }
        }
        return result;
    }

    public static String resolveAppCustomEvent(ExtractedFields fields) {
        if (fields.getCustomEventType() == null) {
            return null;
        }
        return WorkOrderAnalysisV3Constants.APP_EVENT_TYPES.contains(fields.getCustomEventType())
                ? fields.getCustomEventType()
                : null;
    }

    public static ExtractedFields applyLlmRunHourHints(ExtractedFields fields, Map<String, Object> llmHints) {
        if (fields.getRunStartTime() != null && fields.getRunStopTime() != null) {
            return fields;
        }
        if (llmHints == null || llmHints.isEmpty()) {
            return fields;
        }
        Integer start = coerceHour(llmHints.get("run_start_time"));
        Integer stop = coerceHour(llmHints.get("run_stop_time"));
        if (start == null || stop == null) {
            Object nested = llmHints.get("run_hours");
            if (nested instanceof Map<?, ?> map) {
                if (start == null) {
                    start = coerceHour(map.get("start") != null ? map.get("start") : map.get("run_start_time"));
                }
                if (stop == null) {
                    stop = coerceHour(map.get("stop") != null ? map.get("stop") : map.get("run_stop_time"));
                }
            }
        }
        if (start == null || stop == null) {
            return fields;
        }
        fields.setRunStartTime(start);
        fields.setRunStopTime(stop);
        fields.getSources().put("run_hours", "llm_fallback");
        return fields;
    }

    private static Integer coerceHour(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return clampHour(Integer.parseInt(String.valueOf(value).strip()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer clampHour(int value) {
        if (value >= 0 && value <= 23) {
            return value;
        }
        if (value == 24) {
            return 0;
        }
        return null;
    }

    private static Integer[] hoursFromFragment(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).strip();
        if (text.isEmpty() || RUN_HOURS_IMMEDIATE.matcher(text).find()) {
            return null;
        }
        if (ISO_FULL.matcher(text).matches()) {
            return null;
        }
        Matcher match = HOURS_FRAGMENT.matcher(text);
        if (!match.find()) {
            return null;
        }
        Integer start = clampHour(Integer.parseInt(match.group(1)));
        Integer stop = clampHour(Integer.parseInt(match.group(2)));
        if (start == null || stop == null) {
            return null;
        }
        return new Integer[] {start, stop};
    }

    private static Integer[] parseRunHours(String text, Object... candidates) {
        for (Object candidate : candidates) {
            Integer[] parsed = hoursFromFragment(candidate);
            if (parsed != null) {
                return parsed;
            }
        }
        if (candidates.length >= 2) {
            Object startRaw = candidates[candidates.length - 2];
            Object stopRaw = candidates[candidates.length - 1];
            try {
                if (startRaw != null
                        && stopRaw != null
                        && String.valueOf(startRaw).strip().matches("\\d{1,2}")
                        && String.valueOf(stopRaw).strip().matches("\\d{1,2}")) {
                    Integer start =
                            clampHour(Integer.parseInt(String.valueOf(startRaw).strip()));
                    Integer stop =
                            clampHour(Integer.parseInt(String.valueOf(stopRaw).strip()));
                    if (start != null && stop != null) {
                        return new Integer[] {start, stop};
                    }
                }
            } catch (NumberFormatException ignore) {
                // fall through
            }
        }
        Matcher labeled = RUN_HOURS_LABELED.matcher(text == null ? "" : text);
        if (labeled.find()) {
            Integer start = clampHour(Integer.parseInt(labeled.group(1)));
            Integer stop = clampHour(Integer.parseInt(labeled.group(2)));
            if (start != null && stop != null) {
                return new Integer[] {start, stop};
            }
        }
        String body = text == null ? "" : text;
        Matcher bare = RUN_HOURS_BARE.matcher(body);
        while (bare.find()) {
            int windowStart = Math.max(0, bare.start() - 12);
            String window = body.substring(windowStart, Math.min(body.length(), bare.end() + 4));
            if (Pattern.compile("年龄|age|岁", Pattern.CASE_INSENSITIVE)
                    .matcher(window)
                    .find()) {
                continue;
            }
            if (Pattern.compile("投放|时间|时段|小时|点|上午|下午|晚上").matcher(window).find()) {
                Integer start = clampHour(Integer.parseInt(bare.group(1)));
                Integer stop = clampHour(Integer.parseInt(bare.group(2)));
                if (start != null && stop != null) {
                    return new Integer[] {start, stop};
                }
            }
        }
        return new Integer[] {null, null};
    }

    private static Object fieldValue(Map<String, Object> llmFields, String key) {
        Object raw = llmFields.get(key);
        if (raw instanceof Map<?, ?> map) {
            Object normalized = map.get("normalized_value");
            if (normalized != null) {
                return normalized;
            }
            return map.get("value");
        }
        return raw;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).strip();
            if (!text.isEmpty() && !"null".equals(text)) {
                return text;
            }
        }
        return null;
    }

    private static Object firstValue(Object... values) {
        for (Object value : values) {
            if (value == null || "".equals(value)) {
                continue;
            }
            if (value instanceof List<?> list && list.isEmpty()) {
                continue;
            }
            if (value instanceof Map<?, ?> map && map.isEmpty()) {
                continue;
            }
            return value;
        }
        return null;
    }

    private record BrandResolved(String name, String source) {}

    private static BrandResolved resolveDisplayName(Map<String, Object> structured, Map<String, Object> llmFields) {
        Object[][] candidates = {
            {structured.get("project_name"), "work_order"},
            {structured.get("product_name"), "work_order"},
            {structured.get("brand_name"), "work_order"},
            {fieldValue(llmFields, "project_name"), "llm"},
            {fieldValue(llmFields, "product_name"), "llm"},
            {fieldValue(llmFields, "brand_name"), "llm"},
        };
        for (Object[] candidate : candidates) {
            String normalized = normalizeBrandName(candidate[0]);
            if (normalized != null) {
                return new BrandResolved(normalized, String.valueOf(candidate[1]));
            }
        }
        return new BrandResolved(null, null);
    }

    static String normalizeBrandName(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        if (text.isEmpty()) {
            return null;
        }
        String[] parts = BRAND_VALUE_END.split(text, 2);
        text = parts[0].strip();
        text = WorkOrderParser.stripTrailingFields(text);
        if (text == null || text.isEmpty()) {
            return null;
        }
        text = BRAND_PREFIX.matcher(text).replaceFirst("").strip();
        if (!BRAND_TRAILING_PERCENTAGE_RANGE.matcher(text).find()) {
            text = BRAND_TRAILING_PERCENTAGES.matcher(text).replaceFirst("").strip();
        }
        text = text.replaceAll("^[ \\t\\-－_]+|[ \\t\\-－_]+$", "");
        return text.isEmpty() ? null : text;
    }

    private static String matchGroup(Pattern pattern, String text) {
        Matcher match = pattern.matcher(text);
        return match.find() ? match.group(1).strip() : null;
    }

    private static Double parseFloat(Object... values) {
        for (Object value : values) {
            if (value == null || "".equals(value)) {
                continue;
            }
            try {
                double number = Double.parseDouble(
                        String.valueOf(value).replace(",", "").strip());
                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException ignore) {
                // next
            }
        }
        return null;
    }

    private static int[] parseAges(String text, Map<String, Object> structured, Map<String, Object> llm) {
        Matcher rangeM = AGE_RANGE.matcher(text);
        if (rangeM.find()) {
            return new int[] {clampAge(Integer.parseInt(rangeM.group(1))), clampAge(Integer.parseInt(rangeM.group(2)))};
        }
        Integer ageMin = parseInt(structured.get("age_min"), fieldValue(llm, "age_min"), matchGroup(AGE_MIN, text));
        Integer ageMax = parseInt(structured.get("age_max"), fieldValue(llm, "age_max"), matchGroup(AGE_MAX, text));
        Matcher bare =
                Pattern.compile("\\b(\\d{1,2})\\s*[-~到至]\\s*(\\d{1,2})\\s*岁?").matcher(text);
        if (ageMin == null && ageMax == null && bare.find()) {
            return new int[] {clampAge(Integer.parseInt(bare.group(1))), clampAge(Integer.parseInt(bare.group(2)))};
        }
        if (ageMin != null && ageMax != null && ageMin > ageMax) {
            int tmp = ageMin;
            ageMin = ageMax;
            ageMax = tmp;
        }
        return new int[] {ageMin == null ? -1 : ageMin, ageMax == null ? -1 : ageMax};
    }

    private static Integer parseInt(Object... values) {
        for (Object value : values) {
            if (value == null || "".equals(value)) {
                continue;
            }
            Matcher match = Pattern.compile("\\d{1,3}").matcher(String.valueOf(value));
            if (match.find()) {
                return clampAge(Integer.parseInt(match.group(0)));
            }
        }
        return null;
    }

    private static int clampAge(int value) {
        return Math.max(13, Math.min(65, value));
    }

    private static String normalizeGender(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip().toLowerCase(Locale.ROOT);
        if (List.of("all", "不限", "不限性别", "男女不限", "any").contains(text)) {
            return "all";
        }
        if (List.of("male", "男", "男性", "m").contains(text)) {
            return "male";
        }
        if (List.of("female", "女", "女性", "f").contains(text)) {
            return "female";
        }
        if (value.contains("女") && !value.contains("男")) {
            return "female";
        }
        if (value.contains("男") && !value.contains("女")) {
            return "male";
        }
        if (value.contains("不限")) {
            return "all";
        }
        return null;
    }

    private static String inferGender(String text) {
        if (Pattern.compile("不限性别|性别不限").matcher(text).find()) {
            return "all";
        }
        if (Pattern.compile("仅女性|女性用户|women|female\\s*only", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            return "female";
        }
        if (Pattern.compile("仅男性|男性用户|men|male\\s*only", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) {
            return "male";
        }
        return null;
    }

    private static String firstNonAppUrl(String text) {
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String url = matcher.group(0).replaceAll("[.,;，。]+$", "");
            String host = hostOf(url);
            if (host.contains("play.google.com")
                    || host.contains("apps.apple.com")
                    || host.contains("itunes.apple.com")) {
                continue;
            }
            return url;
        }
        return null;
    }

    private static String firstAppStoreUrl(String text) {
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String url = matcher.group(0).replaceAll("[.,;，。]+$", "");
            String host = hostOf(url);
            if (host.contains("play.google.com")
                    || host.contains("apps.apple.com")
                    || host.contains("itunes.apple.com")) {
                return url;
            }
        }
        return null;
    }

    private static String storeFromUrl(String url) {
        String host = hostOf(url);
        if (host.contains("play.google.com")) {
            return "google_play";
        }
        if (host.contains("apps.apple.com") || host.contains("itunes.apple.com")) {
            return "itunes";
        }
        return "google_play";
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return url.toLowerCase(Locale.ROOT);
        }
    }

    private static String normalizeIso(String value) {
        String text = value.strip().replace(' ', 'T');
        if (text.endsWith("Z")
                || Pattern.compile("[+-]\\d{2}:?\\d{2}$").matcher(text).find()) {
            return text;
        }
        if (text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
            return text + ":00Z";
        }
        if (text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
            return text + "Z";
        }
        return text;
    }

    private static String findEventLabel(String text) {
        String stripped = URL.matcher(text).replaceAll(" ");
        Object[][] patterns = {
            {"\\bPURCHASE\\b", "PURCHASE"},
            {"\\bADD_TO_CART\\b", "ADD_TO_CART"},
            {"\\bINITIATED_CHECKOUT\\b", "INITIATED_CHECKOUT"},
            {"\\bCOMPLETE_REGISTRATION\\b", "COMPLETE_REGISTRATION"},
            {"\\bSUBSCRIBE\\b", "SUBSCRIBE"},
            {"\\bLEAD\\b", "LEAD"},
            {"\\bROAS\\b", "ROAS"},
            {"购买|下单", "PURCHASE"},
            {"首充", "首充"},
            {"注册", "COMPLETE_REGISTRATION"},
            {"加购|加入购物车", "ADD_TO_CART"},
        };
        for (Object[] pair : patterns) {
            if (Pattern.compile(String.valueOf(pair[0]), Pattern.CASE_INSENSITIVE)
                    .matcher(stripped)
                    .find()) {
                return String.valueOf(pair[1]);
            }
        }
        return null;
    }

    private static String mapEventToken(String eventName) {
        String upper = eventName.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (upper) {
            case "PURCHASE" -> "PURCHASE";
            case "ADD_TO_CART" -> "ADD_TO_CART";
            case "INITIATED_CHECKOUT" -> "INITIATED_CHECKOUT";
            case "COMPLETE_REGISTRATION" -> "COMPLETE_REGISTRATION";
            case "SUBSCRIBE" -> "SUBSCRIBE";
            case "LEAD" -> "LEAD";
            case "REGISTER" -> "COMPLETE_REGISTRATION";
            default -> {
                if ("首充".equals(eventName)) {
                    yield "PURCHASE";
                }
                yield null;
            }
        };
    }
}
