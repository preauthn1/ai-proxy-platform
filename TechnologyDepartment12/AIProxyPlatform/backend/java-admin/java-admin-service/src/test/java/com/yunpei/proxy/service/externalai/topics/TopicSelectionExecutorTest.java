package com.yunpei.proxy.service.externalai.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunpei.proxy.service.llm.LlmProviderException;
import com.yunpei.proxy.service.llm.MockLlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 选题执行器测试：Mock 结果形状 + request_id + 混合语言 title 规范化。
 */
class TopicSelectionExecutorTest {

    private static final String MIXED_CN_PRODUCT_TOPICS_JSON = """
            {"topics":[
              {"title":"多肽5: Discover a clearer benefit","angle":"聚焦一个清晰的核心价值，降低理解门槛。","audience":"面向目标市场的潜在兴趣用户，结合现有投放设置持续测试与优化。","selling_points":["围绕产品核心价值建立清晰、可测试的广告沟通。"],"rationale":"将产品价值与当前投放目标结合，便于在 Facebook 广告中测试用户反馈。"},
              {"title":"多肽5: See a different way to start","angle":"用易于尝试的第一步建立行动意愿。","audience":"面向目标市场的潜在兴趣用户，结合现有投放设置持续测试与优化。","selling_points":["围绕产品核心价值建立清晰、可测试的广告沟通。"],"rationale":"将产品价值与当前投放目标结合，便于在 Facebook 广告中测试用户反馈。"},
              {"title":"多肽5: Choose the experience that fits","angle":"从使用体验与选择感切入广告沟通。","audience":"面向目标市场的潜在兴趣用户，结合现有投放设置持续测试与优化。","selling_points":["围绕产品核心价值建立清晰、可测试的广告沟通。"],"rationale":"将产品价值与当前投放目标结合，便于在 Facebook 广告中测试用户反馈。"}
            ]}
            """;

    @Test
    void execute_withMock_returnsStableTopicShape() {
        Map<String, Object> payload = Map.of("product_name", "Demo Product", "count", 2, "country", "US");

        Map<String, Object> result = TopicSelectionExecutor.execute(new MockLlmProvider(), payload, "ext-req-1");

        assertThat(result.get("request_id")).isEqualTo("ext-req-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) result.get("topics");
        assertThat(topics).hasSize(2);
        Map<String, Object> first = topics.get(0);
        assertThat(first.get("title")).asString().contains("Demo Product");
        assertThat(first.get("angle")).asString().isNotBlank();
        assertThat(first.get("audience")).asString().contains("目标市场");
        assertThat(first.get("selling_points")).isInstanceOf(List.class);
        assertThat((List<?>) first.get("selling_points")).isNotEmpty();
        assertThat(first.get("rationale")).asString().isNotBlank();
    }

    @Test
    void execute_withChineseProductName_onUsMarket_keepsMixedTitles() {
        Map<String, Object> payload = Map.of("product_name", "多肽5", "count", 3, "country", "US");

        Map<String, Object> result = TopicSelectionExecutor.execute(new MockLlmProvider(), payload, "ext-req-cn");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) result.get("topics");
        assertThat(topics).hasSize(3);
        assertThat(topics.get(0).get("title")).asString().startsWith("多肽5:");
        assertThat(topics.get(0).get("title")).asString().contains("Discover");
    }

    @Test
    void normalizeTopics_acceptsMockStyleMixedLanguageTitles() {
        Map<String, Object> context = TopicContextBuilder.build(Map.of("product_name", "多肽5", "country", "US"));

        List<Map<String, Object>> topics =
                TopicSelectionExecutor.normalizeTopics(MIXED_CN_PRODUCT_TOPICS_JSON, context, 3);

        assertThat(topics).hasSize(3);
        assertThat(topics.get(0).get("title")).isEqualTo("多肽5: Discover a clearer benefit");
        assertThat(topics.get(0).get("angle")).asString().contains("核心价值");
        assertThat((List<?>) topics.get(0).get("selling_points")).isNotEmpty();
    }

    @Test
    void normalizeTopics_dropsPureChineseTitle_onNonChineseMarket() {
        Map<String, Object> context = TopicContextBuilder.build(Map.of("product_name", "X", "country", "US"));
        String json =
                "{\"topics\":[{\"title\":\"发现更清晰的产品价值\",\"angle\":\"聚焦核心价值\",\"audience\":\"目标用户\",\"selling_points\":[\"卖点\"],\"rationale\":\"理由\"}]}";

        List<Map<String, Object>> topics = TopicSelectionExecutor.normalizeTopics(json, context, 1);

        assertThat(topics).isEmpty();
    }

    @Test
    void execute_emptyLlmTopics_throwsProviderError() {
        assertThatThrownBy(() -> TopicSelectionExecutor.execute(
                        request -> com.yunpei.proxy.service.llm.LlmResponse.of("{\"topics\":[]}", "x"),
                        Map.of("product_name", "X", "count", 1),
                        "rid"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("no usable");
    }
}
