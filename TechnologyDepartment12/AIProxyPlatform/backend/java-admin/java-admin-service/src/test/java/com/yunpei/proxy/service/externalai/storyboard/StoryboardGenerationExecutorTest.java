package com.yunpei.proxy.service.externalai.storyboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunpei.proxy.service.llm.LlmProviderException;
import com.yunpei.proxy.service.llm.LlmResponse;
import com.yunpei.proxy.service.llm.MockLlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 分镜执行器：Mock 结果形状、空文案失败、轻量 redact。
 */
class StoryboardGenerationExecutorTest {

    @Test
    void execute_withMock_returnsStoryboardShape() {
        Map<String, Object> payload = Map.of(
                "product_name",
                "Demo Product",
                "duration_seconds",
                15,
                "aspect_ratio",
                "16:9",
                "image_urls",
                List.of("https://cdn.example.com/first.png", "https://cdn.example.com/last.png"));

        Map<String, Object> result =
                StoryboardGenerationExecutor.execute(new MockLlmProvider(), payload, "ext-sb-1", "seed-abc");

        assertThat(result.get("request_id")).isEqualTo("ext-sb-1");
        assertThat(result.get("storyboard_text")).asString().isNotBlank();
        assertThat(result.get("duration_seconds")).isEqualTo(15);
        assertThat(result.get("aspect_ratio")).isEqualTo("16:9");
        assertThat(result.get("storyboard_text").toString()).doesNotContain("seed-abc");
        assertThat(result.get("storyboard_text").toString()).doesNotContain("cdn.example.com");
        assertThat(result.get("storyboard_text").toString()).contains("On-screen text: none");
    }

    @Test
    void execute_emptyStoryboardText_throwsProviderError() {
        assertThatThrownBy(() -> StoryboardGenerationExecutor.execute(
                        request -> LlmResponse.of("{\"storyboard_text\":\"   \"}", "x"),
                        Map.of("product_name", "X", "duration_seconds", 12, "aspect_ratio", "9:16"),
                        "rid",
                        "seed"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void redact_replacesCreativeSeedAndImageUrlsLongestFirst() {
        String redacted = StoryboardTextRedactor.redact(
                "seed=seed-long-value url=https://a.example/path/image.png also seed-long",
                "seed-long-value",
                List.of("https://a.example/path/image.png", "https://a.example/path"));
        assertThat(redacted).contains("[private creative seed]");
        assertThat(redacted).contains("[private image URL]");
        assertThat(redacted).doesNotContain("seed-long-value");
        assertThat(redacted).doesNotContain("https://a.example/path/image.png");
    }

    @Test
    void systemPrompt_loadsFromResource() {
        String prompt = StoryboardGenerationExecutor.systemPrompt();
        assertThat(prompt).contains("storyboard_text");
        assertThat(prompt).contains("opening frame");
        assertThat(prompt).contains("never put ad copy in the picture");
        assertThat(prompt).contains("Light inspiration");
        assertThat(prompt).contains("not a shot list");
        assertThat(prompt).contains("advertising-country lock");
        assertThat(prompt).contains("On-screen text: none");
        assertThat(prompt).doesNotContain("or English / Hindi / Hinglish");
    }

    @Test
    void ensureNoOnscreenText_appendsWhenMissing() {
        assertThat(StoryboardGenerationExecutor.ensureNoOnscreenText("one continuous clip"))
                .isEqualTo("one continuous clip " + StoryboardGenerationExecutor.NO_ONSCREEN_TEXT);
        assertThat(StoryboardGenerationExecutor.ensureNoOnscreenText("Hold. On-screen text: none"))
                .isEqualTo("Hold. On-screen text: none");
        assertThat(StoryboardGenerationExecutor.ensureNoOnscreenText("  ")).isEmpty();
    }
}
