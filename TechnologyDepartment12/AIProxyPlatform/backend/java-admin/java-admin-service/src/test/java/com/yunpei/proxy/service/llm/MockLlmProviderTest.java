package com.yunpei.proxy.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link MockLlmProvider} 契约测试（Seam S4）。
 */
class MockLlmProviderTest {

    private final MockLlmProvider provider = new MockLlmProvider();

    @Test
    void complete_topics_returnsJsonStructure() {
        LlmResponse response = provider.complete(LlmRequest.of(
                null,
                "{\"schema_version\":\"external_topics.v2\",\"context\":{\"identity\":{\"canonical_name\":\"Acme\"}},"
                        + "\"limit\":2,\"existing_topics\":[]}",
                "topics"));
        assertThat(response.model()).isEqualTo(MockLlmProvider.MODEL);
        assertThat(response.content()).contains("topics");
        assertThat(response.content()).contains("Acme");
        assertThat(response.content()).contains("selling_points");
        assertThat(response.content()).contains("rationale");
        assertThat(response.content()).contains("Discover a clearer benefit");
        assertThat(response.content()).contains("See a different way to start");
    }

    @Test
    void complete_copy_returnsRequestedCountVariants() {
        LlmResponse response = provider.complete(LlmRequest.of(
                null,
                "{\"schema_version\":\"external_copy.v4\",\"context\":{\"brand\":{\"product_name\":\"Acme\"}},"
                        + "\"requested_count\":2}",
                "copy"));
        assertThat(response.content()).contains("variants");
        assertThat(response.content()).contains("Acme");
        assertThat(response.content()).contains("primary_text");
        assertThat(response.content()).contains("headline");
        assertThat(response.content()).contains("description");
        assertThat(response.content()).contains("Everyday value");
        assertThat(response.content()).contains("Simple start");
    }

    @Test
    void complete_copy_weightMessagingBrief_usesConversationAdTemplates() {
        LlmResponse response = provider.complete(LlmRequest.of(
                null,
                "{\"schema_version\":\"external_copy.v4\",\"context\":{"
                        + "\"brand\":{\"product_name\":\"Peptide 5\"},"
                        + "\"copy_direction\":{\"selected_topic\":\"围绕体重目标规划，鼓励 WhatsApp 沟通\"}"
                        + "},\"requested_count\":2}",
                "copy"));
        assertThat(response.content()).contains("Peptide 5");
        assertThat(response.content()).doesNotContain("多肽");
        assertThat(response.content()).contains("Weight goal plan");
        assertThat(response.content()).contains("Plan your weight goals");
        assertThat(response.content()).contains("Message us");
    }

    @Test
    void complete_workOrderDeliveryFields_returnsFieldsObject() {
        LlmResponse response = provider.complete(LlmRequest.of(null, "投放事件：无事件", "work_order_delivery_fields"));
        assertThat(response.content()).contains("\"fields\"");
    }

    @Test
    void complete_workOrderAnalysisHints_returnsObjectiveJson() {
        LlmResponse response = provider.complete(LlmRequest.of(null, "wts chat +60", "work_order_analysis_hints"));
        assertThat(response.content()).contains("objective");
        assertThat(response.content()).contains("OUTCOME_ENGAGEMENT");
    }

    @Test
    void complete_blankPrompt_throws() {
        assertThatThrownBy(() -> provider.complete(LlmRequest.of(null, "  ", "topics")))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("userPrompt");
    }

    @Test
    void factory_defaultIsMock() {
        LlmProviderFactory factory = new LlmProviderFactory();
        assertThat(factory.provider()).isInstanceOf(MockLlmProvider.class);
    }

    @Test
    void factory_new2apiRoutesToOpenAiCompatible() {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setProvider("new2api");
        LlmProviderFactory factory = new LlmProviderFactory(properties);
        assertThat(factory.provider()).isInstanceOf(OpenAiCompatibleLlmProvider.class);
    }

    @Test
    void factory_openaiCompatibleRoutesToOpenAiCompatible() {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setProvider("openai-compatible");
        LlmProviderFactory factory = new LlmProviderFactory(properties);
        assertThat(factory.provider()).isInstanceOf(OpenAiCompatibleLlmProvider.class);
    }
}
