package com.yunpei.proxy.service.image;

import java.util.Map;
import java.util.UUID;

/**
 * 占位 Provider：按 size 生成假 URL（{@code https://img.example/generated/<uuid>.png}）。
 *
 * <p>不校验入参、不发网络请求；用于打通图片生成管线，后续由真实 Provider 替换。
 *
 * @author yunpei
 */
public class PlaceholderImageProvider implements ImageProvider {

    private static final String BASE = "https://img.example/generated/";

    @Override
    public GeneratedImage generate(ImageBrief brief) {
        String url = BASE + UUID.randomUUID() + ".png";
        return new GeneratedImage(brief.imageIndex(), url, null, brief.prompt(), brief.size(), null, Map.of());
    }
}
