package com.yunpei.proxy.service.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PlaceholderImageProvider} 契约测试：返回 URL 非空、size 透传。
 */
class PlaceholderImageProviderTest {

    private final PlaceholderImageProvider provider = new PlaceholderImageProvider();

    @Test
    void generate_returnsNonBlankUrl() {
        GeneratedImage image = provider.generate(ImageBrief.of(1, "9:16", "a cat"));

        assertThat(image.url()).isNotBlank();
        assertThat(image.url()).startsWith("https://img.example/generated/");
        assertThat(image.url()).endsWith(".png");
        assertThat(image.storageKey()).isNull();
        assertThat(image.metadata()).isEmpty();
    }

    @Test
    void generate_sizePassthrough() {
        GeneratedImage image = provider.generate(ImageBrief.of(3, "1:1", "a dog"));

        assertThat(image.size()).isEqualTo("1:1");
        assertThat(image.index()).isEqualTo(3);
        assertThat(image.prompt()).isEqualTo("a dog");
    }

    @Test
    void generate_producesUniqueUrls() {
        ImageBrief brief = ImageBrief.of(1, "9:16", "a cat");
        String first = provider.generate(brief).url();
        String second = provider.generate(brief).url();

        assertThat(first).isNotEqualTo(second);
    }
}
