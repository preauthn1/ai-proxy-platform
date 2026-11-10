package com.yunpei.proxy.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频任务详情（GET data；严格 snake_case）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "视频生成任务详情")
public class ExternalVideoGenerationJobVO {

    @JsonProperty("job_id")
    @Schema(description = "任务 id")
    private String jobId;

    @Schema(description = "状态：processing | succeeded | failed")
    private String status;

    @JsonProperty("duration_seconds")
    @Schema(description = "时长秒")
    private Integer durationSeconds;

    @JsonProperty("aspect_ratio")
    @Schema(description = "宽高比")
    private String aspectRatio;

    @Schema(description = "成功时视频 URL")
    private String url;

    @Schema(description = "失败时错误文案")
    private String error;
}
