package com.yunpei.proxy.service.externalai.storyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 分镜文案轻量 redact：替换 creative_seed 与 image_urls（对齐 Python
 * {@code _redact_external_storyboard_private_values}）。
 *
 * @author yunpei
 */
public final class StoryboardTextRedactor {

    public static final String CREATIVE_SEED_PLACEHOLDER = "[private creative seed]";
    public static final String IMAGE_URL_PLACEHOLDER = "[private image URL]";

    private StoryboardTextRedactor() {}

    /**
     * 按私有值长度降序替换，避免短 URL 抢先切碎长 URL。
     */
    public static String redact(String value, String creativeSeed, List<String> imageUrls) {
        if (value == null) {
            return "";
        }
        String text = value;
        List<String[]> replacements = new ArrayList<>();
        if (creativeSeed != null && !creativeSeed.isBlank()) {
            replacements.add(new String[] {creativeSeed, CREATIVE_SEED_PLACEHOLDER});
        }
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.isBlank()) {
                    replacements.add(new String[] {url, IMAGE_URL_PLACEHOLDER});
                }
            }
        }
        replacements.sort(
                Comparator.comparingInt((String[] pair) -> pair[0].length()).reversed());
        for (String[] pair : replacements) {
            text = text.replace(pair[0], pair[1]);
        }
        return text;
    }
}
