package com.yunpei.proxy.service.externalvideo;

import java.util.List;

/**
 * 创建视频生成任务入参命令。
 *
 * <p>{@code storyboardText} 可空：为空时由 Service 在 start 阶段内部调用分镜生成。
 *
 * @param externalRequestId 幂等键；可选
 * @param images 源图列表；空或恰好 2 张
 * @param storyboardText 分镜文案；可选（与 productName 至少其一）
 * @param durationSeconds 时长秒
 * @param aspectRatio 宽高比
 * @param productName 产品名；无 storyboardText 时用于内部分镜
 * @param brief 运营 brief；可选
 * @param prompt 额外分镜提示；可选
 * @param language 语言提示；可选
 * @author yunpei
 */
public record CreateVideoGenerationCommand(
        String externalRequestId,
        List<String> images,
        String storyboardText,
        Integer durationSeconds,
        String aspectRatio,
        String productName,
        String brief,
        String prompt,
        String language) {

    public static CreateVideoGenerationCommand of(
            String externalRequestId,
            List<String> images,
            String storyboardText,
            Integer durationSeconds,
            String aspectRatio) {
        return of(externalRequestId, images, storyboardText, durationSeconds, aspectRatio, null, null, null, null);
    }

    public static CreateVideoGenerationCommand of(
            String externalRequestId,
            List<String> images,
            String storyboardText,
            Integer durationSeconds,
            String aspectRatio,
            String productName,
            String brief,
            String prompt,
            String language) {
        return new CreateVideoGenerationCommand(
                externalRequestId,
                images,
                storyboardText,
                durationSeconds,
                aspectRatio,
                productName,
                brief,
                prompt,
                language);
    }
}
