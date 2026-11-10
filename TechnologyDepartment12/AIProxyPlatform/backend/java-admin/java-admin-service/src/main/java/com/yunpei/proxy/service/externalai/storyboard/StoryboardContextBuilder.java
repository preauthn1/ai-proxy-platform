package com.yunpei.proxy.service.externalai.storyboard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分镜请求规范化与 LLM user context（对齐 Python external storyboard 精简路径；不做 image resolve）。
 *
 * @author yunpei
 */
public final class StoryboardContextBuilder {

    public static final String GENERATION_PROFILE = "external_storyboard_indian_aaa_v1";
    public static final int DEFAULT_DURATION_SECONDS = 12;
    public static final int MIN_DURATION_SECONDS = 1;
    public static final int MAX_DURATION_SECONDS = 300;
    public static final String DEFAULT_ASPECT_RATIO = "9:16";
    public static final int MAX_IMAGE_URLS = 2;
    public static final int MAX_ASPECT_RATIO_LENGTH = 32;
    public static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private StoryboardContextBuilder() {}

    /**
     * 构建写入 metadata 的轻量字段。
     */
    public static Map<String, Object> buildMetadata(String identity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generation_profile", GENERATION_PROFILE);
        metadata.put("india_date", currentIndiaDate());
        metadata.put("creative_seed", creativeSeed(identity));
        return metadata;
    }

    /**
     * 构建 LLM user JSON context（text-only；image_urls 作为引用传入，不下载）。
     */
    public static Map<String, Object> buildUserContext(Map<String, Object> payload, String indiaDate) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        List<String> imageUrls = normalizeImageUrls(body.get("image_urls"));
        int duration = resolveDurationSeconds(body.get("duration_seconds"));
        String aspect = resolveAspectRatio(body.get("aspect_ratio"));

        Map<String, Object> context = new LinkedHashMap<>();
        putIfPresent(context, "product_name", clean(body.get("product_name")));
        putIfPresent(context, "brief", clean(body.get("brief")));
        putIfPresent(context, "prompt", clean(body.get("prompt")));
        putIfPresent(context, "language", clean(body.get("language")));
        context.put("duration_seconds", duration);
        context.put("aspect_ratio", aspect);
        context.put("india_date", clean(indiaDate) == null ? currentIndiaDate() : clean(indiaDate));
        context.put("image_contract", imageContract(imageUrls));
        context.put("image_urls", imageUrls);
        return context;
    }

    public static String currentIndiaDate() {
        return LocalDate.now(INDIA_ZONE).toString();
    }

    /**
     * {@code sha256("external_video_storyboard:{identity}")[:24]}。
     */
    public static String creativeSeed(String identity) {
        String source = "external_video_storyboard:" + (identity == null ? "" : identity);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public static int resolveDurationSeconds(Object raw) {
        if (raw == null) {
            return DEFAULT_DURATION_SECONDS;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_DURATION_SECONDS;
        }
    }

    public static String resolveAspectRatio(Object raw) {
        String text = clean(raw);
        return text == null ? DEFAULT_ASPECT_RATIO : text;
    }

    public static List<String> normalizeImageUrls(Object raw) {
        List<String> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            String url = clean(item);
            if (url == null) {
                continue;
            }
            out.add(url);
            if (out.size() >= MAX_IMAGE_URLS) {
                break;
            }
        }
        return out;
    }

    private static String imageContract(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return "text_only";
        }
        if (imageUrls.size() == 1) {
            return "first_frame";
        }
        return "first_frame_then_last_frame";
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isEmpty())) {
            map.put(key, value);
        }
    }

    private static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
