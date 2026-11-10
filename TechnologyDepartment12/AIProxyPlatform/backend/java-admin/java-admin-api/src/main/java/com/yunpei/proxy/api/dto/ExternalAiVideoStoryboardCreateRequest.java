package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 分镜创建请求（对齐 Python {@code ExternalAIVideoStoryboardCreate}）。
 *
 * @author yunpei
 */
@Data
@Schema(description = "外部 AI 视频分镜创建请求")
public class ExternalAiVideoStoryboardCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    private String language;

    @JsonProperty("product_name")
    @Schema(description = "产品名；必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productName;

    private String brief;

    @JsonProperty("image_urls")
    private List<String> imageUrls = new ArrayList<>();

    @JsonProperty("duration_seconds")
    private Integer durationSeconds = 12;

    @JsonProperty("aspect_ratio")
    private String aspectRatio = "9:16";

    private String prompt;
}
