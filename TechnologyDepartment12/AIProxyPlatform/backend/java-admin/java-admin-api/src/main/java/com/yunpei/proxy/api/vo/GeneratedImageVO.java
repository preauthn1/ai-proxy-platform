package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成结果图 VO（对齐 Python {@code ExternalGeneratedImageRead}）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "生成结果图")
public class GeneratedImageVO {

    @Schema(description = "图下标（1-based）")
    private Integer index;

    @Schema(description = "公开 URL")
    private String url;

    @Schema(description = "图对应 prompt")
    private String prompt;
}
