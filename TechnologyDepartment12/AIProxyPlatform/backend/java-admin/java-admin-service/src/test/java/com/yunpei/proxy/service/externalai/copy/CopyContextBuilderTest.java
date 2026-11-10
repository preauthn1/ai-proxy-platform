package com.yunpei.proxy.service.externalai.copy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 文案 context 组装：白名单字段与 count/长度默认。
 */
class CopyContextBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void build_includesBrandMarketAndCopyDirection() {
        Map<String, Object> payload = Map.of(
                "product_name",
                "Acme App",
                "country",
                "US",
                "brief",
                "Focus on onboarding ease",
                "selling_points",
                List.of("Fast setup", "Clear value"),
                "audience",
                "Mobile gamers");

        Map<String, Object> context = CopyContextBuilder.build(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> brand = (Map<String, Object>) context.get("brand");
        assertThat(brand.get("product_name")).isEqualTo("Acme App");
        @SuppressWarnings("unchecked")
        Map<String, Object> market = (Map<String, Object>) context.get("market");
        assertThat(market.get("country_code")).isEqualTo("US");
        @SuppressWarnings("unchecked")
        Map<String, Object> direction = (Map<String, Object>) context.get("copy_direction");
        assertThat(direction.get("creative_brief")).isEqualTo("Focus on onboarding ease");
        assertThat(direction.get("selected_topic")).isEqualTo("Focus on onboarding ease");
        assertThat(direction.get("strategy_instruction")).asString().contains("free-form operator input");
        assertThat(direction.get("selling_points")).isEqualTo(List.of("Fast setup", "Clear value"));
    }

    @Test
    void build_withoutBrief_includesSelfAngleInstruction() {
        Map<String, Object> payload = Map.of(
                "product_name", "Acme App",
                "country", "US",
                "selling_points", List.of("Fast setup"));

        Map<String, Object> context = CopyContextBuilder.build(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> direction = (Map<String, Object>) context.get("copy_direction");
        assertThat(direction.get("creative_brief")).isNull();
        assertThat(direction.get("strategy_instruction")).asString().contains("No operator brief");
        assertThat(direction.get("selling_points")).isEqualTo(List.of("Fast setup"));
    }

    @Test
    void build_stripsWorkOrderMetadataFromProductName_andAudienceTargeting() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_name", "多肽5 投放国家：美国 投放时间：21-7 投放媒体：fb 投放人群：年龄18-65 WhatsApp：+85251252290");
        payload.put("country", "US");
        payload.put("brief", "围绕“体重目标规划”建立话题，把品牌定位为用户了解体重管理选择的入口，鼓励通过 WhatsApp 与客服或顾问沟通。");
        payload.put(
                "selling_points",
                List.of(
                        "强调从一次私信开始了解可选方案，契合 WhatsApp 对话承接。",
                        "可测试“目标规划”“日常坚持”“更清晰的信息”这类温和动机。",
                        "画面建议使用计划表、水杯、轻食、步行等通用健康生活元素，保持合规且贴近移动信息流。"));
        payload.put("audience", "对制定健康生活计划、控制饮食节奏、管理体重目标或寻找新选择有兴趣的美国成年人；用户画像仅用于投放规划，不在前端文案中点名个人属性。");
        payload.put(
                "adset",
                Map.of(
                        "age_min",
                        18,
                        "age_max",
                        65,
                        "gender",
                        "all",
                        "whatsAppNumber",
                        "1252290",
                        "whatsAppAreaCode",
                        "8525"));

        Map<String, Object> context = CopyContextBuilder.build(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> brand = (Map<String, Object>) context.get("brand");
        // 英文市场：中文品名本地化为拉丁展示名，避免广告里突兀夹杂汉字
        assertThat(brand.get("product_name")).isEqualTo("Peptide 5");

        @SuppressWarnings("unchecked")
        Map<String, Object> audience = (Map<String, Object>) context.get("audience");
        assertThat(audience).containsOnlyKeys("description");
        assertThat(audience.get("description").toString()).contains("体重目标");

        @SuppressWarnings("unchecked")
        Map<String, Object> direction = (Map<String, Object>) context.get("copy_direction");
        @SuppressWarnings("unchecked")
        List<String> points = (List<String>) direction.get("selling_points");
        assertThat(points).hasSize(2);
        assertThat(points).noneMatch(p -> p.contains("画面建议"));

        String serialized = MAPPER.writeValueAsString(context);
        // brand.product_name 不得再含中文品名；工单投放字段不得泄漏
        assertThat(serialized).doesNotContain("多肽");
        for (String leak : List.of(
                "投放国家",
                "投放时间",
                "投放媒体",
                "投放人群",
                "WhatsApp：+852",
                "+85251252290",
                "1252290",
                "age_min",
                "age_max",
                "21-7")) {
            assertThat(serialized).doesNotContain(leak);
        }
    }

    @Test
    void localizeProductNameForCopy_mapsChineseTokensOnEnglishMarket() {
        Map<String, Object> english =
                Map.of("label", "English", "instruction", "Use English for user-facing ad content.");
        assertThat(CopyContextBuilder.localizeProductNameForCopy("多肽5", english))
                .isEqualTo("Peptide 5");
        assertThat(CopyContextBuilder.localizeProductNameForCopy("Acme", english))
                .isEqualTo("Acme");
        Map<String, Object> chinese =
                Map.of("label", "Chinese", "instruction", "Use Chinese for user-facing ad content.");
        assertThat(CopyContextBuilder.localizeProductNameForCopy("多肽5", chinese))
                .isEqualTo("多肽5");
    }

    @Test
    void consumerSellingPoints_dropsVisualOperatorNotes() {
        List<String> points = CopyContextBuilder.consumerSellingPoints(
                List.of("从一次私信开始了解可选方案", "画面建议使用计划表和水杯", "Visual direction: lifestyle table shot"));
        assertThat(points).containsExactly("从一次私信开始了解可选方案");
    }

    @Test
    void resolveCount_defaultsToOne() {
        assertThat(CopyContextBuilder.resolveCount(null)).isEqualTo(1);
        assertThat(CopyContextBuilder.resolveCount("x")).isEqualTo(1);
        assertThat(CopyContextBuilder.resolveCount(3)).isEqualTo(3);
    }

    @Test
    void lengthLimits_usesDefaultsAndConstraintOverrides() {
        Map<String, Integer> defaults = CopyContextBuilder.lengthLimits(Map.of());
        assertThat(defaults.get("primary_text")).isEqualTo(500);
        assertThat(defaults.get("headline")).isEqualTo(40);
        assertThat(defaults.get("description")).isEqualTo(80);

        Map<String, Integer> overridden =
                CopyContextBuilder.lengthLimits(Map.of("constraints", Map.of("headline_max_length", 25)));
        assertThat(overridden.get("headline")).isEqualTo(25);
        assertThat(overridden.get("primary_text")).isEqualTo(500);
    }
}
