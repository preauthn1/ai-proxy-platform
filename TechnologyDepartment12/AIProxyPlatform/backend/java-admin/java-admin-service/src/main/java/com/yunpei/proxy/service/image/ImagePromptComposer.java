package com.yunpei.proxy.service.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将生图系统提示词套到用户 brief 上：文案当意图，不把广告字画进画面。
 *
 * <p>对外回传的 prompt 仍用用户原文；仅上游请求体使用合成结果。
 */
public final class ImagePromptComposer {

    static final String PROMPT_RESOURCE = "external-ai/image-system-prompt.txt";

    private static final AtomicReference<String> SYSTEM_PROMPT = new AtomicReference<>();

    private ImagePromptComposer() {}

    /**
     * 合成发给生图模型的 prompt。
     *
     * @param userPrompt 调用方 brief（可为空则抛错）
     * @return 系统约束 + 用户 brief
     */
    public static String apply(String userPrompt) {
        String user = userPrompt == null ? "" : userPrompt.trim();
        if (user.isEmpty()) {
            throw new ImageProviderException("prompt is required");
        }
        return systemPrompt() + "\n\nUser brief (intent only, not on-image copy):\n" + user;
    }

    static String systemPrompt() {
        String cached = SYSTEM_PROMPT.get();
        if (cached != null) {
            return cached;
        }
        try (InputStream in = ImagePromptComposer.class.getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) {
                throw new ImageProviderException("missing image system prompt resource: " + PROMPT_RESOURCE);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                throw new ImageProviderException("image system prompt resource is empty");
            }
            SYSTEM_PROMPT.compareAndSet(null, text);
            return SYSTEM_PROMPT.get();
        } catch (IOException ex) {
            throw new ImageProviderException("failed to load image system prompt: " + ex.getMessage());
        }
    }
}
