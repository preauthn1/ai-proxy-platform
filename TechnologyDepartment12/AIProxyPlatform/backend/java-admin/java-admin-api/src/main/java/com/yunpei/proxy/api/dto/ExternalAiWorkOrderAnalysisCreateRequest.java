package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单分析创建请求（对齐 Python {@code ExternalAIWorkOrderAnalysisCreate}）。
 *
 * @author yunpei
 */
@Data
@Schema(description = "外部 AI 工单分析创建请求")
public class ExternalAiWorkOrderAnalysisCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    @Schema(description = "语言；可选")
    private String language;

    @JsonProperty("work_order_type")
    @Schema(description = "工单类型；必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workOrderType;

    @JsonProperty("work_order_text")
    @Schema(description = "工单正文；必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workOrderText;

    @Schema(description = "投放媒介；必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private String media;
}
