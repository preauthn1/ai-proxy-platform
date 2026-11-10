package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * 选题创建请求（对齐 Python {@code ExternalAITopicSelectionCreate}）。
 *
 * @author yunpei
 */
@Data
@Schema(description = "外部 AI 选题创建请求")
public class ExternalAiTopicSelectionCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    private String language;

    @JsonProperty("product_name")
    @Schema(description = "产品名；必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productName;

    private String brief;
    private String country;

    @JsonProperty("work_order_type")
    private String workOrderType;

    private String industry;
    private Integer count = 3;
    private Map<String, Object> campaign = new LinkedHashMap<>();
    private Map<String, Object> adset = new LinkedHashMap<>();
    private Map<String, Object> creative = new LinkedHashMap<>();
}
