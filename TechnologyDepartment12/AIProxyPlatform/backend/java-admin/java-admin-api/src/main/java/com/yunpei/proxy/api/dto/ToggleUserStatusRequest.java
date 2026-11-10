package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 启停用户请求：{@code status} 或 {@code isEnabled}，取值 0|1。
 *
 * @author yunpei
 */
@Data
@Schema(description = "启停用户")
public class ToggleUserStatusRequest {

    @Schema(description = "0=禁用 1=启用", example = "1")
    private Integer status;

    @Schema(description = "兼容字段，等同 status")
    private Integer isEnabled;
}
