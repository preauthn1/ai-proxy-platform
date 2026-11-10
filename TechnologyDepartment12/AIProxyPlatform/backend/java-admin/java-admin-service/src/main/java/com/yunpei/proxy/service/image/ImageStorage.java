package com.yunpei.proxy.service.image;

/**
 * 图片存储抽象：将已生成的 sourceUrl 传输到对象存储，并返回公开可访问 URL。
 *
 * <p>实现：{@code PlaceholderImageStorage}（直通/内存）、{@code S3ImageStorage}（{@code oss.enabled=true}）。
 *
 * @author yunpei
 */
public interface ImageStorage {

    /**
     * 传输一张已生成的图片。
     *
     * @param sourceUrl 源图片 URL（由 Provider 生成）
     * @param jobId 所属任务 id（用于对象 key 组织）
     * @param index 图下标（1-based）
     * @return 传输结果（含 publicUrl 与 storageKey）
     */
    TransferResult transfer(String sourceUrl, String jobId, int index);

    /**
     * 持久化用户上传的源图（edits），返回公开 URL 与 storageKey。
     *
     * @param data 图片字节（非空）
     * @param contentType 已探测的 MIME（如 image/png）
     * @return 上传结果
     */
    TransferResult storeUploadedSourceImage(byte[] data, String contentType);

    /**
     * 按 storageKey 读取图片并编码为 data URL；不可用时返回 null。
     *
     * @param storageKey 对象 key
     * @return {@code data:<mime>;base64,...} 或 null
     */
    String dataUrlForStorageKey(String storageKey);
}
