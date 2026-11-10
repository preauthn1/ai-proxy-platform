package com.yunpei.proxy.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务详情 VO（GET 信封的 data；对齐 Python {@code ExternalImageGenerationJobRead}，snake_case）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "图片生成任务详情")
public class ExternalImageGenerationJobVO {

    @JsonProperty("job_id")
    @Schema(description = "任务 id")
    private String jobId;

    @Schema(description = "状态：succeeded | failed | processing")
    private String status;

    @Schema(description = "生成图数量")
    private Integer count;

    @Schema(description = "尺寸")
    private String size;

    @JsonProperty("model_id")
    @Schema(description = "模型 id；可为 null")
    private String modelId;

    @Schema(description = "生成结果图；非 succeeded 为空")
    private List<GeneratedImageVO> images;

    @Schema(description = "对外错误文案；failed 时")
    private String error;

    @JsonProperty("source_job_id")
    @Schema(description = "修订源任务 id")
    private String sourceJobId;

    @Schema(description = "模式：generate | edit | from_image")
    private String mode;
}
