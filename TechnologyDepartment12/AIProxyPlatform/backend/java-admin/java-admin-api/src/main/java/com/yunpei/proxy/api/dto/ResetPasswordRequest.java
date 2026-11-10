package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求。
 *
 * @author yunpei
 */
@Data
@Schema(description = "重置用户密码")
public class ResetPasswordRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "新密码明文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
