package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图片修订创建请求（对齐 Python {@code ExternalImageRevisionCreate}）。
 *
 * @author yunpei
 */
@Data
@Schema(description = "图片修订创建请求")
public class ExternalImageRevisionCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    @Schema(description = "修改要求；必填，≤2000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String feedback;

    @JsonProperty("source_image_index")
    @Schema(description = "源图 1-based 下标；默认 1", example = "1")
    private Integer sourceImageIndex;

    @Schema(description = "生成数量；可空则继承源任务，1..5")
    private Integer count;

    @Schema(description = "尺寸；可空则继承源任务")
    private String size;

    @JsonProperty("model_id")
    @Schema(description = "模型 id；可空则继承源任务，≤128")
    private String modelId;
}
