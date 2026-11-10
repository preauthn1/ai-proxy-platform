package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 创建视频生成任务请求（外部集成；字段名严格 snake_case）。
 *
 * <p>校验在 Service：{@code storyboard_text} 与 {@code product_name} 至少其一；
 * images 为空或恰好 2 张；duration 1..300；{@code prompt} ≤4000。无分镜文案时由内部生成。
 *
 * @author yunpei
 */
@Data
@Schema(description = "创建视频生成任务请求")
public class ExternalVideoGenerationCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    @Schema(description = "源图：省略/空=文生视频；恰好 2 张=首尾帧（HTTPS URL 或 base64）")
    private List<String> images = new ArrayList<>();

    @JsonProperty("storyboard_text")
    @Schema(description = "分镜文案；可选。为空时由内部根据 product_name/brief/prompt/images 生成")
    private String storyboardText;

    @JsonProperty("product_name")
    @Schema(description = "产品名；无 storyboard_text 时必填，用于内部分镜")
    private String productName;

    @Schema(description = "运营 brief；可选，参与内部分镜")
    private String brief;

    @Schema(description = "额外分镜提示；可选，≤4000", maxLength = 4000)
    private String prompt;

    @Schema(description = "语言提示；可选")
    private String language;

    @JsonProperty("duration_seconds")
    @Schema(description = "时长秒；默认 12，范围 1..300", example = "12")
    private Integer durationSeconds;

    @JsonProperty("aspect_ratio")
    @Schema(description = "宽高比；默认 9:16", example = "9:16")
    private String aspectRatio;
}
