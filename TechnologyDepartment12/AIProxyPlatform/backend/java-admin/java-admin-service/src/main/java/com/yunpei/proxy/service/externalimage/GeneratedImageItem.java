package com.yunpei.proxy.service.externalimage;

/**
 * 单张生成结果图（对齐 Python {@code ExternalGeneratedImageRead}）。
 *
 * @param index 图下标（1-based）
 * @param url 公开 URL
 * @param prompt 图对应 prompt
 * @author yunpei
 */
public record GeneratedImageItem(int index, String url, String prompt) {}
