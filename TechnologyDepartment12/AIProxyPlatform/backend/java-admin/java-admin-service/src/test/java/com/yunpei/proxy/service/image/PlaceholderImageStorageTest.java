package com.yunpei.proxy.service.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PlaceholderImageStorage} 契约测试：transfer 返回 storageKey；上传可 dataUrl 回读。
 */
class PlaceholderImageStorageTest {

    private final PlaceholderImageStorage storage = new PlaceholderImageStorage();

    @Test
    void transfer_publicUrlEqualsSourceUrlAndHasStorageKey() {
        TransferResult result = storage.transfer("https://img.example/generated/abc.png", "job-1", 1);

        assertThat(result.publicUrl()).isEqualTo("https://img.example/generated/abc.png");
        assertThat(result.storageKey()).isNotBlank();
        assertThat(storage.dataUrlForStorageKey(result.storageKey())).startsWith("data:image/png;base64,");
    }

    @Test
    void storeUploaded_andDataUrlRoundTrip() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        TransferResult stored = storage.storeUploadedSourceImage(png, "image/png");

        assertThat(stored.storageKey()).startsWith("placeholder://external-image-source/");
        String dataUrl = storage.dataUrlForStorageKey(stored.storageKey());
        assertThat(dataUrl).startsWith("data:image/png;base64,");
    }
}
