package com.yunpei.proxy.service.image;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 占位存储：进程内字节表；{@code transfer} 对 data URL 落盘字节，对 http(s) 记入最小 PNG 占位，
 * 保证 revision 可拿到 {@code storage_key} → data URL。
 *
 * <p>当 {@code oss.enabled=true} 时由 {@code S3ImageStorage} 接管；缺省或 false 时使用本实现。
 *
 * @author yunpei
 */
@Component
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PlaceholderImageStorage implements ImageStorage {

    /** 1x1 透明 PNG。 */
    private static final byte[] TINY_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final ConcurrentMap<String, byte[]> blobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> mimeTypes = new ConcurrentHashMap<>();

    @Override
    public TransferResult transfer(String sourceUrl, String jobId, int index) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ImageProviderException("source image url is blank");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new ImageProviderException("jobId is blank");
        }
        int safeIndex = Math.max(index, 1);
        String source = sourceUrl.trim();
        String key = "placeholder://external-image/" + jobId.trim() + "/" + safeIndex + ".png";
        if (source.regionMatches(true, 0, "data:", 0, 5)) {
            DecodedDataUrl decoded = decodeDataUrl(source);
            blobs.put(key, decoded.bytes());
            mimeTypes.put(key, decoded.mimeType());
        } else {
            blobs.put(key, TINY_PNG);
            mimeTypes.put(key, "image/png");
        }
        return new TransferResult(source, key);
    }

    @Override
    public TransferResult storeUploadedSourceImage(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new ImageProviderException("Uploaded source image is empty.");
        }
        ImageContentType detected = ImageContentTypes.detect(data);
        String mime = contentType == null || contentType.isBlank() ? detected.mimeType() : contentType;
        String key = "placeholder://external-image-source/" + UUID.randomUUID() + detected.extension();
        blobs.put(key, data.clone());
        mimeTypes.put(key, mime);
        String publicUrl = "https://img.example/storage/" + key.replace("placeholder://", "");
        return new TransferResult(publicUrl, key);
    }

    @Override
    public String dataUrlForStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        byte[] bytes = blobs.get(storageKey);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String mime = mimeTypes.getOrDefault(storageKey, "image/png");
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static DecodedDataUrl decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new ImageProviderException("invalid data URL: missing comma");
        }
        String meta = dataUrl.substring(0, comma);
        String payload = dataUrl.substring(comma + 1);
        if (payload.isBlank()) {
            throw new ImageProviderException("invalid data URL: empty payload");
        }
        String mime = "image/png";
        int semi = meta.indexOf(';');
        if (meta.regionMatches(true, 0, "data:", 0, 5)) {
            int end = semi > 5 ? semi : comma;
            if (end > 5) {
                String m = meta.substring(5, end).trim();
                if (!m.isEmpty()) {
                    mime = m;
                }
            }
        }
        try {
            byte[] bytes;
            if (meta.toLowerCase(java.util.Locale.ROOT).contains(";base64")) {
                bytes = Base64.getDecoder().decode(payload);
            } else {
                bytes = java.net.URLDecoder.decode(payload, java.nio.charset.StandardCharsets.UTF_8)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            return new DecodedDataUrl(bytes, mime);
        } catch (IllegalArgumentException e) {
            throw new ImageProviderException("invalid data URL base64: " + e.getMessage(), e);
        }
    }

    private record DecodedDataUrl(byte[] bytes, String mimeType) {}
}
