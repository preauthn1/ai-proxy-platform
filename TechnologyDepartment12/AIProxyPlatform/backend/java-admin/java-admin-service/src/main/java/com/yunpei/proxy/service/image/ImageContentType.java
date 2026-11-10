package com.yunpei.proxy.service.image;

/**
 * 图片内容探测结果。
 *
 * @param mimeType MIME，如 {@code image/png}
 * @param extension 含点后缀，如 {@code .png}
 * @author yunpei
 */
public record ImageContentType(String mimeType, String extension) {}
