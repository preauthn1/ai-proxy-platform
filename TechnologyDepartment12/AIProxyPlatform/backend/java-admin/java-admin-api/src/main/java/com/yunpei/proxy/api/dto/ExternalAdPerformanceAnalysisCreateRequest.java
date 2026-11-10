package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 外部广告效果分析创建请求（对齐 Python {@code ExternalAdPerformanceAnalysisCreate}）。
 *
 * @author yunpei
 */
@Data
@Schema(description = "外部广告效果分析创建请求")
public class ExternalAdPerformanceAnalysisCreateRequest {

    @JsonProperty("external_request_id")
    @Schema(description = "幂等键；必填，1–128", requiredMode = Schema.RequiredMode.REQUIRED)
    private String externalRequestId;

    @JsonProperty("source_type")
    @Schema(description = "来源类型；默认 external")
    private String sourceType = "external";

    @JsonProperty("date_preset")
    private String datePreset;

    @JsonProperty("date_start")
    private String dateStart;

    @JsonProperty("date_stop")
    private String dateStop;

    private Map<String, Object> campaign = new LinkedHashMap<>();
    private Map<String, Object> adset = new LinkedHashMap<>();
    private Map<String, Object> creative = new LinkedHashMap<>();
    private Map<String, Object> insight = new LinkedHashMap<>();
    private List<Map<String, Object>> siblings = new ArrayList<>();

    @JsonProperty("metadata_json")
    private Map<String, Object> metadataJson = new LinkedHashMap<>();

    /** Python extra=allow：保留未建模顶层字段参与幂等 hash。 */
    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        extra.put(key, value);
    }

    /** 组装为 Service 入参 Map（跳过 null）。 */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "external_request_id", externalRequestId);
        put(payload, "source_type", sourceType);
        put(payload, "date_preset", datePreset);
        put(payload, "date_start", dateStart);
        put(payload, "date_stop", dateStop);
        put(payload, "campaign", campaign);
        put(payload, "adset", adset);
        put(payload, "creative", creative);
        put(payload, "insight", insight);
        put(payload, "siblings", siblings);
        put(payload, "metadata_json", metadataJson);
        for (Map.Entry<String, Object> entry : extra.entrySet()) {
            put(payload, entry.getKey(), entry.getValue());
        }
        return payload;
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
