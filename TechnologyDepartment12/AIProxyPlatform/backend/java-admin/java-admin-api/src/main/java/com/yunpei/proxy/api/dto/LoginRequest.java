package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * @author yunpei
 */
@Data
@Schema(description = "账号密码 + ALTCHA 登录请求")
public class LoginRequest {

    @Schema(
            description = "用户名",
            example = "root",
            minLength = 1,
            maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不超过 64")
    private String username;

    @Schema(
            description = "密码(明文,仅登录时使用)",
            example = "123456",
            minLength = 1,
            maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不超过 64")
    private String password;

    @Schema(description = "ALTCHA PoW payload（Base64）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "不能为空")
    private String altcha;
}
