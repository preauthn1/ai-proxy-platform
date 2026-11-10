package com.yunpei.proxy.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 生图系统提示词：套到 brief 上，且不把广告长文当画面字。
 */
class ImagePromptComposerTest {

    @Test
    void apply_prefixesSystemRulesAndKeepsUserBrief() {
        String composed = ImagePromptComposer.apply(
                "Explore Peptide at Your Pace\nLooking for a calmer way to approach weight goals?");

        assertThat(composed).contains("intent only");
        assertThat(composed).contains("Do not typeset headlines");
        assertThat(composed).contains("Lead with the core product");
        assertThat(composed).contains("Explore Peptide at Your Pace");
        assertThat(composed).startsWith(ImagePromptComposer.systemPrompt());
    }

    @Test
    void apply_blank_throws() {
        assertThatThrownBy(() -> ImagePromptComposer.apply("  "))
                .isInstanceOf(ImageProviderException.class)
                .hasMessageContaining("prompt is required");
    }

    @Test
    void systemPrompt_loadsFromResource() {
        assertThat(ImagePromptComposer.systemPrompt()).contains("photoreal still");
        assertThat(ImagePromptComposer.systemPrompt()).contains("Visible text: none");
    }
}
