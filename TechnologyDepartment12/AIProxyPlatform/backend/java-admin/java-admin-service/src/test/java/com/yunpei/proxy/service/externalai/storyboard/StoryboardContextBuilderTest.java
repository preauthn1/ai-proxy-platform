package com.yunpei.proxy.service.externalai.storyboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 分镜 context / 默认值 / creative_seed 规范化。
 */
class StoryboardContextBuilderTest {

    @Test
    void resolveDuration_defaultsWhenMissingOrInvalid() {
        assertThat(StoryboardContextBuilder.resolveDurationSeconds(null))
                .isEqualTo(StoryboardContextBuilder.DEFAULT_DURATION_SECONDS);
        assertThat(StoryboardContextBuilder.resolveDurationSeconds(12)).isEqualTo(12);
        assertThat(StoryboardContextBuilder.resolveDurationSeconds("20")).isEqualTo(20);
    }

    @Test
    void resolveAspectRatio_defaults() {
        assertThat(StoryboardContextBuilder.resolveAspectRatio(null))
                .isEqualTo(StoryboardContextBuilder.DEFAULT_ASPECT_RATIO);
        assertThat(StoryboardContextBuilder.resolveAspectRatio(" 16:9 ")).isEqualTo("16:9");
    }

    @Test
    void normalizeImageUrls_trimsAndKeepsOrderUpToTwo() {
        List<String> urls = StoryboardContextBuilder.normalizeImageUrls(
                List.of("  https://a/1.png  ", "https://a/2.png", "https://a/3.png"));
        assertThat(urls).containsExactly("https://a/1.png", "https://a/2.png");
    }

    @Test
    void creativeSeed_isStableForSameIdentity() {
        String a = StoryboardContextBuilder.creativeSeed("req-1");
        String b = StoryboardContextBuilder.creativeSeed("req-1");
        String c = StoryboardContextBuilder.creativeSeed("req-2");
        assertThat(a).isEqualTo(b).hasSize(24);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void buildUserContext_includesImageContractAndUrls() {
        Map<String, Object> payload = Map.of(
                "product_name",
                "Demo",
                "brief",
                "promo",
                "duration_seconds",
                18,
                "aspect_ratio",
                "9:16",
                "image_urls",
                List.of("https://cdn/first.png"));
        Map<String, Object> ctx = StoryboardContextBuilder.buildUserContext(payload, "2026-08-10");
        assertThat(ctx.get("duration_seconds")).isEqualTo(18);
        assertThat(ctx.get("aspect_ratio")).isEqualTo("9:16");
        assertThat(ctx.get("india_date")).isEqualTo("2026-08-10");
        assertThat(ctx.get("image_contract")).isEqualTo("first_frame");
        assertThat(ctx.get("image_urls")).isEqualTo(List.of("https://cdn/first.png"));
        assertThat(ctx.get("product_name")).isEqualTo("Demo");
    }
}
