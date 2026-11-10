package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 文案创建请求（对齐 Python {@code ExternalAICopyGenerationCreate}；兼容 customEventType 别名）。
 *
 * @author yunpei
 */
@Data
@Schema(description = "外部 AI 文案创建请求")
public class ExternalAiCopyGenerationCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；可选，≤128")
    private String externalRequestId;

    private String language;

    @JsonProperty("product_name")
    @Schema(description = "产品名；必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productName;

    @JsonProperty("landing_url")
    private String landingUrl;

    private String audience;
    private String country;

    @JsonProperty("event_name")
    private String eventName;

    /** Python 客户端使用 camelCase {@code customEventType}。 */
    @JsonProperty("custom_event_type")
    @JsonAlias("customEventType")
    private String customEventType;

    private String brief;

    @JsonProperty("selling_points")
    private List<String> sellingPoints = new ArrayList<>();

    private Map<String, Object> constraints = new LinkedHashMap<>();
    private Map<String, Object> campaign = new LinkedHashMap<>();
    private Map<String, Object> adset = new LinkedHashMap<>();
    private Map<String, Object> creative = new LinkedHashMap<>();
    private Integer count = 1;
}
