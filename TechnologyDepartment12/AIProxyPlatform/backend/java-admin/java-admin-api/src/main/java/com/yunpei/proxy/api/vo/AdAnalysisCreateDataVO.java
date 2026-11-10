package com.yunpei.proxy.api.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST 创建成功 data。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "广告分析任务创建结果")
public class AdAnalysisCreateDataVO {

    @JsonProperty("analysis_id")
    private String analysisId;

    @JsonProperty("external_request_id")
    private String externalRequestId;

    private String status;
    private String stage;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("poll_url")
    private String pollUrl;

    @JsonProperty("idempotent_replay")
    private boolean idempotentReplay;
}
