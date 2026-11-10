package com.yunpei.proxy.service.externalimage;

/**
 * 上传图编辑（edits）入参。
 *
 * @param imageBytes 源图字节
 * @param declaredContentType 客户端声明 Content-Type
 * @param prompt 编辑提示
 * @param count 1..5
 * @param size 尺寸
 * @param modelId 模型
 * @param externalRequestId 幂等键
 * @author yunpei
 */
public record CreateImageEditCommand(
        byte[] imageBytes,
        String declaredContentType,
        String prompt,
        Integer count,
        String size,
        String modelId,
        String externalRequestId) {}
