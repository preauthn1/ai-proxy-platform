package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部 AI 文本生成信封（对齐 Python {@code ExternalAIGenerationEnvelope}）。
 *
 * <p>code：0=success，1001=processing，4001=validation_error，5001=provider_error。
 * 不落入全局 {@code Result}。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部 AI 文本生成响应信封")
public class ExternalAiGenerationEnvelope {

    @Schema(description = "业务码", example = "1001")
    private int code;

    @Schema(description = "消息", example = "processing")
    private String message;

    @Schema(description = "业务数据（snake_case 字段）")
    private Object data;
}
