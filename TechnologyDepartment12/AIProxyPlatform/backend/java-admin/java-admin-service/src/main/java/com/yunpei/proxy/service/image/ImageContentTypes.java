package com.yunpei.proxy.service.image;

import java.util.Locale;
import java.util.Set;

/**
 * 上传图片 MIME 探测与白名单（对齐 Python {@code image_content_type} / edits 校验）。
 *
 * @author yunpei
 */
public final class ImageContentTypes {

    /** 允许的声明 Content-Type（小写）。 */
    public static final Set<String> ALLOWED_UPLOAD_CONTENT_TYPES =
            Set.of("image/gif", "image/jpeg", "image/jpg", "image/png", "image/svg+xml", "image/webp");

    private ImageContentTypes() {}

    /**
     * 规范化声明的 content-type（去掉 charset 等参数）。
     *
     * @param contentType 原始 Content-Type
     * @return 小写主类型；空则 null
     */
    public static String normalizeDeclaredContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String main = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(main)) {
            return "image/jpeg";
        }
        return main.isEmpty() ? null : main;
    }

    /**
     * 声明类型是否在白名单内。
     *
     * @param contentType 原始或规范化类型
     * @return true 若允许
     */
    public static boolean isAllowedUploadContentType(String contentType) {
        String normalized = normalizeDeclaredContentType(contentType);
        if (normalized == null) {
            return false;
        }
        // 白名单含 image/jpg，规范化后也可能是 image/jpeg
        return ALLOWED_UPLOAD_CONTENT_TYPES.contains(normalized)
                || ALLOWED_UPLOAD_CONTENT_TYPES.contains("image/jpeg".equals(normalized) ? "image/jpg" : normalized);
    }

    /**
     * 按魔数探测图片类型；无法识别则抛 {@link ImageProviderException}。
     *
     * @param data 图片字节
     * @return 探测结果
     */
    public static ImageContentType detect(byte[] data) {
        if (data == null || data.length == 0) {
            throw new ImageProviderException("Uploaded image content is invalid or unsupported");
        }
        // PNG
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return new ImageContentType("image/png", ".png");
        }
        // JPEG
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return new ImageContentType("image/jpeg", ".jpg");
        }
        // GIF
        if (data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a') {
            return new ImageContentType("image/gif", ".gif");
        }
        // WEBP: RIFF....WEBP
        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return new ImageContentType("image/webp", ".webp");
        }
        // SVG（文本）
        String head = new String(data, 0, Math.min(data.length, 256), java.nio.charset.StandardCharsets.UTF_8)
                .trim()
                .toLowerCase(Locale.ROOT);
        if (head.startsWith("<?xml") || head.startsWith("<svg")) {
            return new ImageContentType("image/svg+xml", ".svg");
        }
        throw new ImageProviderException("Uploaded image content is invalid or unsupported");
    }

    /**
     * 将 MIME 映射为扩展名。
     *
     * @param mimeType MIME
     * @return 含点后缀
     */
    public static String extensionForMime(String mimeType) {
        if (mimeType == null) {
            return ".png";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };
    }
}
