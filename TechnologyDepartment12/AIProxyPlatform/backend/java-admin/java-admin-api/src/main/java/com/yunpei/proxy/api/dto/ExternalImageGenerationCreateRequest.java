package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建图片生成任务请求（外部集成；extra=forbid）。
 *
 * <p>校验在 {@code ExternalImageGenerationService} 内完成：prompt 非空、count 1..5、
 * external_request_id / model_id ≤128、size 默认 "9:16"。
 *
 * @author yunpei
 */
@Data
@Schema(description = "创建图片生成任务请求")
public class ExternalImageGenerationCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    @Schema(description = "提示词；必填，非空", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prompt;

    @Schema(description = "生成图数量；默认 1，1..5", example = "1")
    private Integer count;

    @Schema(description = "尺寸；默认 9:16", example = "9:16")
    private String size;

    @JsonProperty("model_id")
    @Schema(description = "模型 id；可选，≤128")
    private String modelId;
}
