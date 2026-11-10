package com.yunpei.proxy.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET 轮询 data。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "广告分析任务轮询结果")
public class AdAnalysisJobDataVO {

    @JsonProperty("analysis_id")
    private String analysisId;

    @JsonProperty("external_request_id")
    private String externalRequestId;

    private String status;
    private String stage;
    private int progress;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("completed_at")
    private String completedAt;

    private Map<String, Object> result;
    private AdAnalysisJobErrorVO error;
}
