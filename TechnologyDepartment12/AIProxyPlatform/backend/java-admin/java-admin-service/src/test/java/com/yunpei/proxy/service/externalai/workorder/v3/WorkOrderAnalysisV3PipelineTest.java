package com.yunpei.proxy.service.externalai.workorder.v3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 工单分析 V3 规则优先管线测试（Seam S3）。
 *
 * <p>样例对齐 Python {@code test_work_order_analysis_v3_objective}。
 */
class WorkOrderAnalysisV3PipelineTest {

    private static final Pattern ENTITY_NAME = Pattern.compile("[A-Z]{6}-\\d{8}");

    @ParameterizedTest
    @CsvSource({
        "OBJ-01, 客户通过 WhatsApp 咨询。应用地址 https://play.google.com/store/apps/details?id=com.x 转化事件 PURCHASE, OUTCOME_ENGAGEMENT, social_or_messaging_destination",
        "OBJ-02, 投放 Instagram 私信互动，希望用户发消息咨询产品。, OUTCOME_ENGAGEMENT, social_or_messaging_destination",
        "OBJ-03, 关联 Facebook 公共主页，用户点击广告后通过 Messenger 对话。, OUTCOME_ENGAGEMENT, social_or_messaging_destination",
        "OBJ-04, 应用推广，Google Play 地址：https://play.google.com/store/apps/details?id=com.demo.game, OUTCOME_APP_PROMOTION, application_destination",
        "OBJ-05, App Store 链接 https://apps.apple.com/app/id123 转化事件 PURCHASE, OUTCOME_APP_PROMOTION, application_destination",
        "OBJ-06, 产品：X。投放事件：无事件。落地页 https://example.com, OUTCOME_TRAFFIC, traffic_event_or_no_event",
        "OBJ-07, 目标是增加落地页访问，链接：https://offers.example.com/uk/balance, OUTCOME_TRAFFIC, traffic_event_or_no_event",
        "OBJ-08, 转化事件为购买 PURCHASE，落地页：https://shop.example.com/eu/trekking, OUTCOME_SALES, conversion_event",
        "OBJ-09, 优化 ROAS 价值，无应用信息，网站购买。, OUTCOME_SALES, conversion_event",
        "OBJ-10, 帮我投一下广告，预算随便。, OUTCOME_TRAFFIC, fallback_traffic",
        "WATCHAPP, 客户误写 WatchApp 联系，日预算 300。, OUTCOME_ENGAGEMENT, social_or_messaging_destination"
    })
    void detectObjective_matrix(String caseId, String text, String expectObjective, String expectRule) {
        ObjectiveDetection result = ObjectiveDetector.detectObjective(text);
        assertThat(result.detectedObjective()).as(caseId).isEqualTo(expectObjective);
        assertThat(result.matchedRule()).as(caseId).isEqualTo(expectRule);
    }

    @Test
    void detectObjective_urlRegisterParam_isFallbackTraffic() {
        String text = """
                项目名称：GAJA777
                投放国家：印度
                投放时间：立即
                投放媒体：fb
                投放人群：年龄18-65
                投放链接：https://www.gaja777.game/#/?invite=YBG71118&register=true
                """;
        ObjectiveDetection result = ObjectiveDetector.detectObjective(text);
        assertThat(result.detectedObjective()).isEqualTo("OUTCOME_TRAFFIC");
        assertThat(result.matchedRule()).isEqualTo("fallback_traffic");
    }

    @Test
    void pipeline_engagement_fixedFields() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "产品：Lucky Harbor。投放马来西亚，年龄 21-45，不限性别，仅移动端。"
                        + "客户点击广告后通过 WhatsApp 咨询。日预算 300，使用广告系列预算，最低成本不设出价上限。"
                        + "素材为竖版图片，立即开始，自动版位。",
                "game",
                "fb",
                "wo-v3-engage-1",
                Map.of(),
                null);

        assertThat(result.get("schema_version")).isEqualTo("3.0");
        assertThat(result.get("request_id")).isEqualTo("wo-v3-engage-1");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_ENGAGEMENT");

        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> adset = (Map<String, Object>) result.get("adset_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> creative = (Map<String, Object>) result.get("creative_payload");

        assertThat(campaign.get("objective")).isEqualTo("OUTCOME_ENGAGEMENT");
        assertThat(campaign.get("status")).isEqualTo("PAUSED");
        assertThat(adset.get("optimization_goal")).isEqualTo("CONVERSATIONS");
        assertThat(adset.get("messaging_channels")).isEqualTo(List.of("WHATSAPP"));
        assertThat(adset.get("destination_type")).isEqualTo("WHATSAPP");
        assertThat(adset.get("messagePlatform")).isEqualTo(List.of("whatsApp"));
        assertThat(adset.get("whatsAppAreaCode")).isEqualTo("");
        assertThat(adset.get("whatsAppNumber")).isEqualTo("");
        assertThat(creative).doesNotContainKey("link");
        assertThat(creative.get("btn_type")).isEqualTo("MESSAGE_PAGE");
        @SuppressWarnings("unchecked")
        List<String> bindings = (List<String>) result.get("required_bindings");
        assertThat(bindings).doesNotContain("pixel");
        for (Map<String, Object> payload : List.of(campaign, adset, creative)) {
            assertThat(ENTITY_NAME.matcher(String.valueOf(payload.get("name"))).matches())
                    .isTrue();
            assertThat(String.valueOf(payload.get("name"))).doesNotContain("WhatsApp");
        }
    }

    @Test
    void pipeline_whatsappPhoneFields() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "客户通过 WhatsApp 咨询，WhatsApp 区号:60 号码:123456789。投放马来西亚，日预算 100。",
                "game",
                "fb",
                "wo-v3-wa-phone",
                Map.of(),
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> adset = (Map<String, Object>) result.get("adset_payload");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_ENGAGEMENT");
        assertThat(adset.get("messagePlatform")).isEqualTo(List.of("whatsApp"));
        assertThat(adset.get("whatsAppAreaCode")).isEqualTo("60");
        assertThat(adset.get("whatsAppNumber")).isEqualTo("123456789");
    }

    @Test
    void pipeline_runHoursOnCampaign() {
        Map<String, Object> result =
                WorkOrderAnalysisV3Pipeline.analyze("""
                项目名称：NightOffer
                投放国家：印度
                投放时间：20-8
                投放事件：无事件
                投放链接：https://example.com/offer
                """, null, "fb", "wo-v3-run-hours", Map.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        assertThat(campaign.get("run_start_time")).isEqualTo(20);
        assertThat(campaign.get("run_stop_time")).isEqualTo(8);
    }

    @Test
    void pipeline_runHoursChinesePoints() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "转化事件 PURCHASE，落地页 https://shop.example.com，投放时段 20点至8点",
                null,
                "fb",
                "wo-v3-run-hours-cn",
                Map.of(),
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        assertThat(campaign.get("run_start_time")).isEqualTo(20);
        assertThat(campaign.get("run_stop_time")).isEqualTo(8);
    }

    @Test
    void pipeline_immediateLaunchOmitsRunHours() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "项目名称：A\n投放时间：立即\n投放事件：无事件\n投放链接：https://example.com",
                null,
                "fb",
                "wo-v3-run-hours-immediate",
                Map.of(),
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        assertThat(campaign).doesNotContainKey("run_start_time");
        assertThat(campaign).doesNotContainKey("run_stop_time");
    }

    @Test
    void pipeline_salesAndOmitNulls() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "品牌：NorthPeak。转化事件为购买 PURCHASE，" + "落地页：https://shop.example.com/eu/trekking。广告系列日预算 1200。",
                null,
                "fb",
                "wo-v3-sales-1",
                Map.of(),
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> adset = (Map<String, Object>) result.get("adset_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> creative = (Map<String, Object>) result.get("creative_payload");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_SALES");
        assertThat(result.get("product_name")).isEqualTo("NorthPeak");
        assertThat(result.get("brand_name")).isEqualTo("NorthPeak");
        assertThat(adset.get("optimization_goal")).isEqualTo("OFFSITE_CONVERSIONS");
        assertThat(adset.get("custom_event_type")).isEqualTo("PURCHASE");
        assertThat(creative.get("btn_type")).isEqualTo("SHOP_NOW");
        assertThat(creative.get("link")).isEqualTo("https://shop.example.com/eu/trekking");
        @SuppressWarnings("unchecked")
        List<String> salesBindings = (List<String>) result.get("required_bindings");
        assertThat(salesBindings).contains("pixel");
        assertThat(adset.values()).doesNotContainNull();
    }

    @Test
    void pipeline_appPromotion() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "Google Play：https://play.google.com/store/apps/details?id=com.example.orbitquest。"
                        + "优化应用内购买事件 PURCHASE。广告组日预算 800，出价上限 12.5。",
                null,
                "fb",
                "wo-v3-app-1",
                Map.of(),
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> adset = (Map<String, Object>) result.get("adset_payload");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_APP_PROMOTION");
        assertThat(campaign.get("budget_type")).isEqualTo("adset");
        assertThat(adset.get("destination_type")).isEqualTo("APP");
        assertThat(adset.get("mobile_app_store")).isEqualTo("google_play");
        assertThat(adset.get("optimization_goal")).isEqualTo("OFFSITE_CONVERSIONS_APP");
        assertThat(adset.get("custom_event_type")).isEqualTo("PURCHASE");
        assertThat(adset.get("bid_strategy")).isEqualTo("LOWEST_COST_WITH_BID_CAP");
        assertThat(adset.get("bid_amount")).isEqualTo(12.5);
        @SuppressWarnings("unchecked")
        List<String> appBindings = (List<String>) result.get("required_bindings");
        assertThat(appBindings).contains("application");
    }

    @Test
    void pipeline_bidCapWithoutAmount_requiresReview() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "落地页访问 https://example.com 希望采用有出价上限的最低成本，但未给金额。", null, "fb", "wo-v3-bid-1", Map.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> review = (Map<String, Object>) result.get("review");
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_TRAFFIC");
        assertThat(review.get("requires_user_review")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) review.get("missing_fields");
        assertThat(missing).contains("bid_amount");
        assertThat(campaign.get("bid_strategy")).isEqualTo("LOWEST_COST_WITHOUT_CAP");
    }

    @Test
    void pipeline_inlineWorkOrder_usesOnlyProjectNameForProductAndBrand() {
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "项目名称：龙虾 投放国家：欧美 投放时间：晚上21点到中午12点 投放媒体：FB 投放事件：购物 投放链接：squishy.velziro.com",
                null,
                "fb",
                "wo-v3-inline-lobster",
                Map.of(),
                null);
        assertThat(result.get("product_name")).isEqualTo("龙虾");
        assertThat(result.get("brand_name")).isEqualTo("龙虾");
    }

    @Test
    void pipeline_projectName_fillsProductAndBrand_andNormalizes() {
        Map<String, Object> result =
                WorkOrderAnalysisV3Pipeline.analyze("""
                项目名称：ron-澳大利亚多肽8%8%
                投放事件：无事件
                """, null, "fb", "wo-v3-project-name-only", Map.of(), null);
        assertThat(result.get("product_name")).isEqualTo("澳大利亚多肽");
        assertThat(result.get("brand_name")).isEqualTo("澳大利亚多肽");
    }

    @Test
    void pipeline_projectName_overridesProductAndBrandLabels() {
        Map<String, Object> llmFields = Map.of(
                "product_name", Map.of("normalized_value", "llm-模型产品8%"),
                "brand_name", Map.of("normalized_value", "llm-模型品牌8%"));
        Map<String, Object> result =
                WorkOrderAnalysisV3Pipeline.analyze("""
                项目名称：ron-澳大利亚多肽8%8%
                品牌名称：ron-项目品牌8%
                产品名称：ron-模型产品8%
                投放事件：无事件
                """, null, "fb", "wo-v3-project-name-priority", llmFields, null);
        assertThat(result.get("product_name")).isEqualTo("澳大利亚多肽");
        assertThat(result.get("brand_name")).isEqualTo("澳大利亚多肽");
    }

    @Test
    void pipeline_wsAbbreviationAndDaypartChinese() {
        String text = """
                项目名称：一鸣中东多肽8%
                投放国家：中东地区
                投放时间：下午15点到晚上21点（北京时间）
                投放媒体：fb
                产品名称：一鸣中东多肽8%
                投放事件：打粉
                ws：852 6093 6241
                """;
        Map<String, Object> result =
                WorkOrderAnalysisV3Pipeline.analyze(text, null, "fb", "wo-v3-ws-yiming", Map.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> adset = (Map<String, Object>) result.get("adset_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> detection = (Map<String, Object>) result.get("objective_detection");

        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_ENGAGEMENT");
        assertThat(detection.get("matched_rule")).isEqualTo("social_or_messaging_destination");
        assertThat(detection.get("matched_value")).isEqualTo("WhatsApp");
        assertThat(result.get("product_name")).isEqualTo("一鸣中东多肽");
        assertThat(result.get("brand_name")).isEqualTo("一鸣中东多肽");
        assertThat(campaign.get("run_start_time")).isEqualTo(15);
        assertThat(campaign.get("run_stop_time")).isEqualTo(21);
        assertThat(adset.get("messaging_channels")).isEqualTo(List.of("WHATSAPP"));
        assertThat(adset.get("destination_type")).isEqualTo("WHATSAPP");
        assertThat(adset.get("messagePlatform")).isEqualTo(List.of("whatsApp"));
        assertThat(adset.get("whatsAppAreaCode")).isEqualTo("852");
        assertThat(adset.get("whatsAppNumber")).isEqualTo("60936241");
    }

    @Test
    void pipeline_llmFallbackFillsObjectiveWhenRulesMiss() {
        String text = """
                项目：测试产品
                联系客户用 wts 聊天：+60-12-3456789
                每天 16 至 22 点投放（非立即）
                """;
        Map<String, Object> bare =
                WorkOrderAnalysisV3Pipeline.analyze(text, null, "fb", "wo-v3-llm-bare", Map.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> bareDetection = (Map<String, Object>) bare.get("objective_detection");
        assertThat(bareDetection.get("matched_rule")).isEqualTo("fallback_traffic");

        Map<String, Object> hints = Map.of(
                "objective",
                "OUTCOME_ENGAGEMENT",
                "confidence",
                0.9,
                "matched_value",
                "WhatsApp",
                "evidence",
                "wts 聊天",
                "messaging",
                Map.of(
                        "channel", "WHATSAPP",
                        "whatsAppAreaCode", "60",
                        "whatsAppNumber", "123456789"),
                "run_start_time",
                16,
                "run_stop_time",
                22);
        Map<String, Object> result =
                WorkOrderAnalysisV3Pipeline.analyze(text, null, "fb", "wo-v3-llm-fill", Map.of(), hints);
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) result.get("campaign_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> adset = (Map<String, Object>) result.get("adset_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> review = (Map<String, Object>) result.get("review");
        @SuppressWarnings("unchecked")
        Map<String, Object> decisions = (Map<String, Object>) review.get("field_decisions");
        @SuppressWarnings("unchecked")
        Map<String, Object> objectiveDecision = (Map<String, Object>) decisions.get("objective");
        @SuppressWarnings("unchecked")
        Map<String, Object> detection = (Map<String, Object>) result.get("objective_detection");

        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_ENGAGEMENT");
        assertThat(detection.get("matched_rule")).isEqualTo("llm_fallback");
        assertThat(review.get("requires_user_review")).isEqualTo(true);
        assertThat(objectiveDecision.get("source")).isEqualTo("llm_fallback");
        assertThat(campaign.get("run_start_time")).isEqualTo(16);
        assertThat(campaign.get("run_stop_time")).isEqualTo(22);
        assertThat(adset.get("messagePlatform")).isEqualTo(List.of("whatsApp"));
        assertThat(adset.get("whatsAppAreaCode")).isEqualTo("60");
        assertThat(adset.get("whatsAppNumber")).isEqualTo("123456789");
    }

    @Test
    void pipeline_llmHintsDoNotOverrideConfidentRule() {
        Map<String, Object> hints = Map.of(
                "objective", "OUTCOME_SALES",
                "confidence", 0.99,
                "matched_value", "should-not-apply");
        Map<String, Object> result = WorkOrderAnalysisV3Pipeline.analyze(
                "客户通过 WhatsApp 咨询。日预算 100。", null, "fb", "wo-v3-no-override", Map.of(), hints);
        @SuppressWarnings("unchecked")
        Map<String, Object> detection = (Map<String, Object>) result.get("objective_detection");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_ENGAGEMENT");
        assertThat(detection.get("matched_rule")).isEqualTo("social_or_messaging_destination");
    }

    @Test
    void executor_llmFailureStillSucceedsRuleOnly() {
        LlmAlwaysFail fail = new LlmAlwaysFail();
        Map<String, Object> result = WorkOrderAnalysisV3Executor.execute(
                fail,
                "转化事件为购买 PURCHASE，落地页：https://shop.example.com/eu/trekking。广告系列日预算 1200。",
                "game",
                "fb",
                "req-rule-only");
        assertThat(result.get("request_id")).isEqualTo("req-rule-only");
        assertThat(result.get("detected_objective")).isEqualTo("OUTCOME_SALES");
        assertThat(result.get("schema_version")).isEqualTo("3.0");
        assertThat(result).containsKeys("campaign_payload", "adset_payload", "creative_payload", "review");
    }

    /** LLM 始终抛错，用于验证 best-effort。 */
    private static final class LlmAlwaysFail implements com.yunpei.proxy.service.llm.LlmProvider {
        @Override
        public com.yunpei.proxy.service.llm.LlmResponse complete(com.yunpei.proxy.service.llm.LlmRequest request) {
            throw new RuntimeException("simulated provider failure");
        }
    }
}
