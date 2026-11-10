package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部集成信封（对齐 Python {@code ExternalImageGenerationEnvelope}）。
 *
 * <p>code：{@code 0=success, 1001=processing, 4001=validation_error, 5001=provider_error}。
 * 该端点不落入全局 {@code Result} 信封。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部集成响应信封")
public class ExternalImageGenerationEnvelope {

    @Schema(description = "业务码", example = "1001")
    private int code;

    @Schema(description = "消息", example = "processing")
    private String message;

    @Schema(description = "业务数据")
    private Object data;
}
