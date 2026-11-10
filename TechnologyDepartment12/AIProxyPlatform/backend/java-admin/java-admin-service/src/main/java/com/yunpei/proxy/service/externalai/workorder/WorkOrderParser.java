package com.yunpei.proxy.service.externalai.workorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工单键值行解析（对齐 Python {@code work_order_parser.parse_work_order_text}）。
 *
 * <p>同一行可含多个「标签：值」，按已知标签切开，避免项目名称吞掉后续投放字段。
 *
 * @author yunpei
 */
public final class WorkOrderParser {

    private static final Map<String, Set<String>> FIELD_ALIASES = Map.ofEntries(
            Map.entry("project_name", Set.of("项目名称", "项目", "工单名称")),
            Map.entry("country", Set.of("投放国家", "国家", "地区", "投放地区")),
            Map.entry("launch_time", Set.of("投放时间", "上线时间", "开始时间")),
            Map.entry("report_timezone", Set.of("日报时区", "时区", "报表时区")),
            Map.entry("media", Set.of("投放媒体", "媒体", "渠道", "平台")),
            Map.entry("event_name", Set.of("投放事件", "事件", "转化事件", "优化事件")),
            Map.entry("audience_description", Set.of("投放人群", "人群", "受众", "目标人群")),
            Map.entry("product_name", Set.of("产品名称", "产品", "品名")),
            Map.entry("brand_name", Set.of("品牌名称", "品牌")),
            Map.entry("payout_amount", Set.of("打款金额", "预算", "金额")),
            Map.entry("service_fee", Set.of("服务费", "费率")),
            Map.entry("business_owner", Set.of("商务", "商务负责人", "销售")),
            Map.entry("landing_url", Set.of("投放链接", "链接", "落地页", "落地页链接", "url", "URL")));

    private static final List<String> SEPARATORS = List.of("：", ":", "；", ";");
    private static final Pattern FIELD_TOKEN = buildFieldTokenPattern();

    private WorkOrderParser() {}

    public static Map<String, Object> parseWorkOrderText(String rawContent) {
        Map<String, String> parsed = new LinkedHashMap<>();
        Map<String, String> unknown = new LinkedHashMap<>();
        if (rawContent == null || rawContent.isBlank()) {
            return new LinkedHashMap<>();
        }
        for (String line : rawContent.replace("\r\n", "\n").split("\n")) {
            String cleaned = line.strip();
            if (cleaned.isEmpty()) {
                continue;
            }
            String[] kv = splitKeyValue(cleaned);
            if (kv[0] == null || kv[1] == null) {
                continue;
            }
            String value = stripTrailingFields(kv[1]);
            if (value == null || value.isEmpty()) {
                continue;
            }
            String normalized = normalizeKey(kv[0]);
            if (normalized != null) {
                parsed.putIfAbsent(normalized, value);
            } else {
                unknown.put(kv[0], value);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(parsed);
        if (!unknown.isEmpty()) {
            result.put("_unknown", unknown);
        }
        return result;
    }

    /**
     * 截到下一个工单字段标签（如「投放国家：」），用于清洗被连写污染的名称。
     */
    public static String stripTrailingFields(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = FIELD_TOKEN.matcher(value);
        if (matcher.find()) {
            return value.substring(0, matcher.start()).strip();
        }
        return value;
    }

    private static Pattern buildFieldTokenPattern() {
        List<String> labels = new ArrayList<>();
        for (Set<String> aliases : FIELD_ALIASES.values()) {
            labels.addAll(aliases);
        }
        labels.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alt = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                alt.append('|');
            }
            alt.append(Pattern.quote(labels.get(i)));
        }
        return Pattern.compile("(?<!\\p{L})(" + alt + ")\\s*[:：]", Pattern.CASE_INSENSITIVE);
    }

    private static String[] splitKeyValue(String line) {
        int bestIndex = -1;
        String bestSep = null;
        for (String sep : SEPARATORS) {
            int index = line.indexOf(sep);
            if (index > 0 && (bestIndex < 0 || index < bestIndex)) {
                bestIndex = index;
                bestSep = sep;
            }
        }
        if (bestIndex < 0 || bestSep == null) {
            return new String[] {null, null};
        }
        String key = line.substring(0, bestIndex).strip().replaceAll("^[：:;；\\s]+|[：:;；\\s]+$", "");
        String value = line.substring(bestIndex + bestSep.length()).strip().replaceAll("^[：:;；\\s]+|[：:;；\\s]+$", "");
        if (key.isEmpty() || value.isEmpty()) {
            return new String[] {null, null};
        }
        return new String[] {key, value};
    }

    private static String normalizeKey(String key) {
        String cleaned = key.strip();
        for (Map.Entry<String, Set<String>> entry : FIELD_ALIASES.entrySet()) {
            if (entry.getValue().contains(cleaned)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
