package com.yunpei.proxy.api.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET 失败任务的 error 对象。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "广告分析任务错误")
public class AdAnalysisJobErrorVO {

    @JsonProperty("error_code")
    private String errorCode;

    private String message;
    private boolean retryable;
}
