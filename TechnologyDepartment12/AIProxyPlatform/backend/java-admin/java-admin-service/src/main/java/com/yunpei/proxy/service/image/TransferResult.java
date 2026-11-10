package com.yunpei.proxy.service.image;

/**
 * 图片传输结果（对齐 Python {@code TransferResult}）。
 *
 * @param publicUrl 公开可访问 URL
 * @param storageKey 对象存储 key（若有；placeholder 阶段为 null）
 * @author yunpei
 */
public record TransferResult(String publicUrl, String storageKey) {}
