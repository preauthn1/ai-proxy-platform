package com.yunpei.proxy.service.externalai.topics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 选题白名单 context 组装测试（对齐 Python {@code test_external_topic_context_*}）。
 */
class TopicContextBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void build_whitelistsFields_andCountryDrivesLanguage() throws Exception {
        Map<String, Object> payload = fullPayload();

        Map<String, Object> context = TopicContextBuilder.build(payload);

        assertThat(context.get("identity")).isEqualTo(Map.of("canonical_name", "GAJA7777"));
        assertThat(context.get("classification")).isEqualTo(Map.of("work_order_type", "live-commerce-growth"));
        assertThat(context.get("campaign"))
                .isEqualTo(Map.of("objective", "OUTCOME_SALES", "special_ad_categories", List.of("NONE")));
        @SuppressWarnings("unchecked")
        Map<String, Object> market = (Map<String, Object>) context.get("market");
        assertThat(market.get("country_code")).isEqualTo("BR");
        @SuppressWarnings("unchecked")
        Map<String, Object> lang = (Map<String, Object>) market.get("target_language");
        assertThat(lang.get("label")).isEqualTo("Brazilian Portuguese");
        assertThat(lang.get("instruction")).isEqualTo("Use Brazilian Portuguese for user-facing ad content.");
        assertThat(context.get("audience")).isEqualTo(Map.of("age_min", 18, "age_max", 65));
        @SuppressWarnings("unchecked")
        Map<String, Object> delivery = (Map<String, Object>) context.get("delivery");
        assertThat(delivery.get("optimization_goal")).isEqualTo("OFFSITE_CONVERSIONS");
        assertThat(delivery.get("event_name")).isEqualTo("PURCHASE");
        assertThat(delivery.get("messaging_channels")).isEqualTo(List.of("WHATSAPP"));
        assertThat(delivery.get("cta")).isEqualTo("SHOP_NOW");
        assertThat(context.get("landing")).isEqualTo(Map.of("domain", "shop.example.test"));
        assertThat(context.get("requested_count")).isEqualTo(3);

        String serialized = MAPPER.writeValueAsString(context);
        for (String privateValue : List.of(
                "private raw work order",
                "legacy-language-must-be-ignored",
                "private campaign name",
                "private ad set name",
                "private creative name",
                "123456789",
                "987654",
                "1234.5",
                "/private/path",
                "token=secret",
                "creative_strategy")) {
            assertThat(serialized).doesNotContain(privateValue);
        }
    }

    @Test
    void build_toleratesMissingOptionalFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_name", "GAJA7777");
        payload.put("count", 1);

        Map<String, Object> context = TopicContextBuilder.build(payload);

        assertThat(context.get("identity")).isEqualTo(Map.of("canonical_name", "GAJA7777"));
        assertThat(context.get("classification")).isEqualTo(Map.of("work_order_type", "unspecified"));
        @SuppressWarnings("unchecked")
        Map<String, Object> market = (Map<String, Object>) context.get("market");
        @SuppressWarnings("unchecked")
        Map<String, Object> lang = (Map<String, Object>) market.get("target_language");
        assertThat(lang.get("label")).isEqualTo("English");
        assertThat(context.get("requested_count")).isEqualTo(1);
        assertThat(context).doesNotContainKeys("audience", "delivery", "landing");
    }

    @Test
    void extractProductName_stripsChineseDeliveryAndWhatsAppTail() {
        assertThat(TopicContextBuilder.extractProductName(
                        "多肽5 投放国家：美国 投放时间：21-7 投放媒体：fb 投放人群：年龄18-65 WhatsApp：+85251252290"))
                .isEqualTo("多肽5");
        assertThat(TopicContextBuilder.extractProductName("BrandX WhatsApp：+85251252290"))
                .isEqualTo("BrandX");
        assertThat(TopicContextBuilder.extractProductName("Clean Name")).isEqualTo("Clean Name");
    }

    private static Map<String, Object> fullPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_request_id", "private-request-id");
        payload.put("product_name", "GAJA7777 Country: Brazil");
        payload.put("brief", "private raw work order and creative request");
        payload.put("language", "legacy-language-must-be-ignored");
        payload.put("country", "BR");
        payload.put("work_order_type", "live-commerce-growth");
        payload.put("industry", "legacy-industry");
        payload.put("count", 3);
        payload.put(
                "campaign",
                Map.of(
                        "name",
                        "private campaign name",
                        "objective",
                        "OUTCOME_SALES",
                        "daily_budget",
                        987654,
                        "special_ad_categories",
                        List.of("NONE")));
        Map<String, Object> adset = new LinkedHashMap<>();
        adset.put("name", "private ad set name");
        adset.put("countries", List.of("BR"));
        adset.put("age_min", 18);
        adset.put("age_max", 65);
        adset.put("gender", "all");
        adset.put("audience_description", "");
        adset.put("optimization_goal", "OFFSITE_CONVERSIONS");
        adset.put("conversion_location", "WEBSITE");
        adset.put("destination_type", "APP");
        adset.put("custom_event_type", "PURCHASE");
        adset.put("messaging_channels", List.of("WHATSAPP"));
        adset.put("messagePlatform", List.of("whatsApp"));
        adset.put("whatsAppAreaCode", "60");
        adset.put("whatsAppNumber", "123456789");
        adset.put("mobile_app_store", "google_play");
        adset.put("object_store_url", "https://play.example.test/store/app?id=private");
        adset.put("device_platforms", List.of("mobile"));
        adset.put("bid_amount", 1234.5);
        payload.put("adset", adset);
        payload.put(
                "creative",
                Map.of(
                        "name",
                        "private creative name",
                        "type",
                        "image",
                        "btn_type",
                        "SHOP_NOW",
                        "link",
                        "https://shop.example.test/private/path?token=secret",
                        "creative_strategy",
                        Map.of("must_not", "leak")));
        return payload;
    }
}
