package com.yunpei.proxy.service.externalai.topics;

import com.yunpei.proxy.service.externalai.workorder.CountryCodes;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 目标语言上下文（对齐 Python {@code build_target_language_context} 的国家驱动子集）。
 *
 * @author yunpei
 */
public final class TargetLanguageContext {

    private static final Map<String, String[]> COUNTRY_RULES = Map.ofEntries(
            Map.entry("AU", new String[] {"Australian English", "Use Australian English for user-facing ad content."}),
            Map.entry("BR", new String[] {"Brazilian Portuguese", "Use Brazilian Portuguese for user-facing ad content."
            }),
            Map.entry("ID", new String[] {"Indonesian", "Use Indonesian for user-facing ad content."}),
            Map.entry("IN", new String[] {
                "Hindi for broad India consumer ads",
                "Use Hindi for broad India consumer ad hooks by default. Use English only when "
                        + "the landing page or work order clearly uses English as the consumer-facing language."
            }),
            Map.entry("MX", new String[] {"Spanish for Mexico", "Use Mexican Spanish for user-facing ad content."}),
            Map.entry("MY", new String[] {
                "Malay or English for Malaysia",
                "Use Malay for broad Malaysia consumer ads. Use English only when the landing page "
                        + "or work order clearly uses English as the consumer-facing language."
            }),
            Map.entry("PH", new String[] {
                "Filipino or English for the Philippines",
                "Use Filipino for broad Philippines consumer ads. Use English only when the landing "
                        + "page or work order clearly uses English as the consumer-facing language."
            }),
            Map.entry("SG", new String[] {"English for Singapore", "Use English for user-facing ad content."}),
            Map.entry("TH", new String[] {"Thai", "Use Thai for user-facing ad content."}),
            Map.entry("US", new String[] {"English", "Use English for user-facing ad content."}),
            Map.entry("VN", new String[] {"Vietnamese", "Use Vietnamese for user-facing ad content."}));

    private TargetLanguageContext() {}

    /**
     * 按国家码（或可解析的国家文本）构建 target_language 对象。
     *
     * @param country 国家码/名称/列表中的首项；可为 null
     * @return 含 country_code / label / instruction 的 map
     */
    public static Map<String, Object> fromCountry(Object country) {
        String countryCode = CountryCodes.normalizeCountryCode(country);
        if (countryCode == null && country != null) {
            String text = String.valueOf(country).trim();
            if (!text.isEmpty() && text.length() == 2 && text.chars().allMatch(Character::isLetter)) {
                countryCode = text.toUpperCase(Locale.ROOT);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("country_code", countryCode == null ? "" : countryCode);
        String[] rule = countryCode == null ? null : COUNTRY_RULES.get(countryCode);
        if (rule != null) {
            out.put("label", rule[0]);
            out.put("instruction", rule[1]);
        } else {
            out.put("label", "English");
            out.put(
                    "instruction",
                    "Infer the most suitable user-facing language from the landing page and work order. "
                            + "If no market language is clear, use English. Do not default to Chinese.");
        }
        return out;
    }
}
