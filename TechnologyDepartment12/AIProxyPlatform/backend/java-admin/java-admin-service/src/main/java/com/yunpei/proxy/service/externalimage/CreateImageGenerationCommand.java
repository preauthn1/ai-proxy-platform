package com.yunpei.proxy.service.externalimage;

/**
 * 创建图片生成任务的入参命令（跨层 DTO；controller 由 {@code ExternalImageGenerationCreateRequest} 转换而来）。
 *
 * @param externalRequestId 幂等键；可选，≤128
 * @param prompt 必填，非空
 * @param count 默认 1，范围 1..5
 * @param size 默认 "9:16"
 * @param modelId 可选，≤128
 * @author yunpei
 */
public record CreateImageGenerationCommand(
        String externalRequestId, String prompt, Integer count, String size, String modelId) {

    public static CreateImageGenerationCommand of(
            String externalRequestId, String prompt, Integer count, String size, String modelId) {
        return new CreateImageGenerationCommand(externalRequestId, prompt, count, size, modelId);
    }
}
