package com.yunpei.proxy.service.externalai.copy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunpei.proxy.service.llm.LlmProviderException;
import com.yunpei.proxy.service.llm.LlmResponse;
import com.yunpei.proxy.service.llm.MockLlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 文案执行器：Mock 结果形状 + 基础校验 + request_id。
 */
class CopyGenerationExecutorTest {

    @Test
    void execute_withMock_returnsStableCopywritingShape() {
        Map<String, Object> payload = Map.of(
                "product_name",
                "Demo Product",
                "count",
                2,
                "country",
                "US",
                "customEventType",
                "PURCHASE",
                "creative",
                Map.of("btn_type", "SHOP_NOW"));

        Map<String, Object> result = CopyGenerationExecutor.execute(new MockLlmProvider(), payload, "ext-copy-1");

        assertThat(result.get("request_id")).isEqualTo("ext-copy-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copywritings = (List<Map<String, Object>>) result.get("copywritings");
        assertThat(copywritings).hasSize(2);
        Map<String, Object> first = copywritings.get(0);
        assertThat(first.get("primary_text")).asString().contains("Demo Product");
        assertThat(first.get("headline")).asString().isNotBlank();
        assertThat(first.get("description")).asString().isNotBlank();
        assertThat(first.get("cta")).isEqualTo("SHOP_NOW");
        assertThat(first.get("call_to_action")).isEqualTo("SHOP_NOW");
        assertThat(first.get("customEventType")).isEqualTo("PURCHASE");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) first.get("copywriting");
        assertThat(nested).containsKeys("primary_text", "headline", "description", "cta");
        assertThat(nested.get("cta")).isEqualTo("SHOP_NOW");
    }

    @Test
    void execute_withDirtyWorkOrderProductName_usesLocalizedEnglishAdCopy() {
        Map<String, Object> payload = Map.of(
                "product_name",
                "多肽5 投放国家：美国 投放时间：21-7 投放媒体：fb 投放人群：年龄18-65 WhatsApp：+85251252290",
                "count",
                3,
                "country",
                "US",
                "brief",
                "围绕体重目标规划建立话题，鼓励通过 WhatsApp 与顾问沟通",
                "creative",
                Map.of("btn_type", "MESSAGE_PAGE"));

        Map<String, Object> result = CopyGenerationExecutor.execute(new MockLlmProvider(), payload, "ext-copy-dirty");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copywritings = (List<Map<String, Object>>) result.get("copywritings");
        assertThat(copywritings).hasSize(3);
        for (Map<String, Object> item : copywritings) {
            String primary = String.valueOf(item.get("primary_text"));
            String headline = String.valueOf(item.get("headline"));
            String description = String.valueOf(item.get("description"));
            // 英文广告：拉丁展示名 + 无汉字/无投放元数据
            assertThat(primary).contains("Peptide 5");
            assertThat(primary + headline + description).doesNotContain("多肽");
            assertThat(primary).doesNotContain("投放国家");
            assertThat(primary).doesNotContain("+852");
            assertThat(headline.length()).isLessThanOrEqualTo(CopyContextBuilder.DEFAULT_HEADLINE_MAX);
        }
        // 体重 + messaging brief → 对话向 headline，而非泛 SaaS 「Discover … today」
        assertThat(copywritings.get(0).get("headline").toString().toLowerCase())
                .containsAnyOf("plan", "message", "weight", "step", "consistent", "option");
        assertThat(copywritings.get(0).get("cta")).isEqualTo("MESSAGE_PAGE");
    }

    @Test
    void acceptVariants_rejectsChineseInEnglishMarketCopy() {
        String content = "{\"variants\":["
                + "{\"primary_text\":\"Try 多肽5 for better goals today with a clear next step.\","
                + "\"headline\":\"Try 多肽5\",\"description\":\"Learn more now\"},"
                + "{\"primary_text\":\"Want a clearer weight plan? Message us to explore options that fit.\","
                + "\"headline\":\"Plan your next step\",\"description\":\"Chat for simple guidance\"}"
                + "]}";
        Map<String, Integer> limits = Map.of(
                "primary_text",
                CopyContextBuilder.DEFAULT_PRIMARY_TEXT_MAX,
                "headline",
                CopyContextBuilder.DEFAULT_HEADLINE_MAX,
                "description",
                CopyContextBuilder.DEFAULT_DESCRIPTION_MAX);
        Map<String, Object> english =
                Map.of("label", "English", "instruction", "Use English for user-facing ad content.");

        List<Map<String, Object>> accepted = CopyGenerationExecutor.acceptVariants(content, limits, 3, english);

        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).get("headline")).isEqualTo("Plan your next step");
    }

    @Test
    void execute_emptyVariants_throwsProviderError() {
        assertThatThrownBy(() -> CopyGenerationExecutor.execute(
                        request -> LlmResponse.of("{\"variants\":[]}", "x"),
                        Map.of("product_name", "X", "count", 1),
                        "rid"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("no usable");
    }

    @Test
    void acceptVariants_rejectsEmptyAndOverLengthFields() {
        String tooLongHeadline = "H".repeat(CopyContextBuilder.DEFAULT_HEADLINE_MAX + 1);
        String content = "{\"variants\":["
                + "{\"primary_text\":\"ok primary text that is long enough\",\"headline\":\"\",\"description\":\"d\"},"
                + "{\"primary_text\":\"ok primary text that is long enough\",\"headline\":\""
                + tooLongHeadline
                + "\",\"description\":\"ok desc\"},"
                + "{\"primary_text\":\"ok primary text that is long enough\",\"headline\":\"Good headline\","
                + "\"description\":\"Useful description\"}"
                + "]}";
        Map<String, Integer> limits = Map.of(
                "primary_text",
                CopyContextBuilder.DEFAULT_PRIMARY_TEXT_MAX,
                "headline",
                CopyContextBuilder.DEFAULT_HEADLINE_MAX,
                "description",
                CopyContextBuilder.DEFAULT_DESCRIPTION_MAX);

        List<Map<String, Object>> accepted = CopyGenerationExecutor.acceptVariants(content, limits, 3);

        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).get("headline")).isEqualTo("Good headline");
    }

    @Test
    void resolveEventType_fallsBackToEventNameNormalization() {
        assertThat(CopyGenerationExecutor.resolveEventType(Map.of("event_name", "purchase")))
                .isEqualTo("PURCHASE");
    }

    @Test
    void normalizeCta_mapsAliases() {
        assertThat(CopyGenerationExecutor.normalizeCta("shop now")).isEqualTo("SHOP_NOW");
        assertThat(CopyGenerationExecutor.normalizeCta("learn-more")).isEqualTo("LEARN_MORE");
    }

    @Test
    void systemPrompt_loadsFromResource() {
        assertThat(CopyGenerationExecutor.systemPrompt()).contains("variants");
        assertThat(CopyGenerationExecutor.systemPrompt()).contains("Facebook/Meta ad copywriter");
        assertThat(CopyGenerationExecutor.systemPrompt()).contains("zero Chinese characters");
        assertThat(CopyGenerationExecutor.systemPrompt()).contains("no upstream topic-selection");
        assertThat(CopyGenerationExecutor.systemPrompt()).contains("creative_brief");
    }
}
