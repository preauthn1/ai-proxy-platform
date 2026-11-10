package com.yunpei.proxy.service.image;

/**
 * 单张图生成请求（对齐 Python {@code ImageBrief}）。
 *
 * @param imageIndex 图下标（1-based）
 * @param size 图片尺寸，如 "9:16"；可空（上游可不传 size）
 * @param prompt 该图 prompt
 * @param model 模型 id；可空则由 Provider 使用配置默认
 * @param referenceImageDataUrl 参考图 data URL；非空且 Provider edit 开启时走 edit API
 * @param revisionInstruction 修订/编辑指令；edit 时优先作为 prompt 发送
 * @author yunpei
 */
public record ImageBrief(
        int imageIndex,
        String size,
        String prompt,
        String model,
        String referenceImageDataUrl,
        String revisionInstruction) {

    /**
     * 兼容旧调用（无 model / 无参考图）。
     */
    public static ImageBrief of(int imageIndex, String size, String prompt) {
        return new ImageBrief(imageIndex, size, prompt, null, null, null);
    }

    /**
     * 是否携带参考图。
     *
     * @return true 当 referenceImageDataUrl 非空
     */
    public boolean hasReferenceImage() {
        return referenceImageDataUrl != null && !referenceImageDataUrl.isBlank();
    }
}
