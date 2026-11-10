package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部广告效果分析信封（对齐 Python {@code AdAnalysisEnvelope}）。
 *
 * <p>code：0=success，1001=accepted，4001=validation_error，5001=server_error。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部广告效果分析响应信封")
public class ExternalAdPerformanceEnvelope {

    @Schema(description = "业务码", example = "1001")
    private int code;

    @Schema(description = "消息", example = "accepted")
    private String message;

    @Schema(description = "业务数据（snake_case）")
    private Object data;
}
