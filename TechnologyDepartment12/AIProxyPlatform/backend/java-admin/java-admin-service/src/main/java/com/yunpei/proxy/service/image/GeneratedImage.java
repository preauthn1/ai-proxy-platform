package com.yunpei.proxy.service.image;

import java.util.Map;

/**
 * 单张生成结果图（对齐 Python {@code GeneratedImage}）。
 *
 * @param index 图下标（1-based）
 * @param url 公开 URL
 * @param storageKey 对象存储 key（若有；placeholder 阶段为 null）
 * @param prompt 图对应 prompt
 * @param size 图片尺寸
 * @param model 使用的模型 id（若有）
 * @param metadata 扩展元数据
 * @author yunpei
 */
public record GeneratedImage(
        int index,
        String url,
        String storageKey,
        String prompt,
        String size,
        String model,
        Map<String, Object> metadata) {}
