package com.yunpei.proxy.service.image;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片生成 Provider 配置（对应 {@code image.*}）。
 *
 * <p>密钥仅通过环境变量注入（{@code NEW2API_API_KEY} / {@code IMAGE_API_KEY}），禁止写入仓库。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "image")
public class ImageProviderProperties {

    /**
     * Provider 名称：{@code placeholder}（默认，单测/本地）或 {@code new2api}（New API 网关）。
     */
    private String provider = "placeholder";

    /**
     * 上传源图（edits）最大字节数；对齐 Python {@code IMAGE_DOWNLOAD_MAX_BYTES} 语义。
     * 默认 10 MiB。
     */
    private long uploadMaxBytes = 10L * 1024 * 1024;

    /** new2api（OpenAI 兼容图片生成）配置。 */
    private New2api new2api = new New2api();

    /**
     * 是否使用 new2api Provider。
     *
     * @return true 当 provider 为 new2api（忽略大小写）
     */
    public boolean isNew2api() {
        return "new2api".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    /** new2api {@code /images/generations} 相关配置（无 size / 无 watermark）。 */
    @Data
    public static class New2api {

        /**
         * API Key；对应 {@code NEW2API_API_KEY}，可回退 {@code IMAGE_API_KEY}（在 yml 中链式占位）。
         */
        private String apiKey;

        /** OpenAI 兼容 base URL（须含 /v1），默认本项目 new2api 接入点。 */
        private String baseUrl = "http://154.89.195.173:3001/v1";

        /** 默认图片模型 id；请求 model_id 非空时覆盖。 */
        private String model = "image";

        /** HTTP 超时秒数。 */
        private int timeoutSeconds = 120;

        /**
         * 失败后的重试等待秒数数组。
         *
         * <p>例如 {@code [1, 2, 5]}：首次立即调用；失败后 sleep 1s 再试，再失败 sleep 2s，
         * 再失败 sleep 5s；仍失败则抛出。空列表表示不重试（仅 1 次尝试）。
         * 负值或 null 元素会被忽略。
         */
        private List<Integer> retryDelaysSeconds = new ArrayList<>(List.of(1, 2, 5));

        /**
         * 是否在 brief 含参考图时调用 edit API；默认 true。
         * 关闭后即使有 reference 也走 {@code /images/generations}。
         */
        private boolean editEnabled = true;

        /**
         * edit API 路径（相对 base-url），默认 {@code /images/edits}。
         */
        private String editPath = "/images/edits";

        /**
         * edit 使用的模型 id；空则回退 {@link #model}。
         */
        private String editModel;
    }
}
