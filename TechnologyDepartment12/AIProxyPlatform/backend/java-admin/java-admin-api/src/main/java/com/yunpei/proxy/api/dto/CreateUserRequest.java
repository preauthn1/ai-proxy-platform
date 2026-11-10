package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.user.UserManageModels.CreateUserCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 创建用户请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateUserCommand.class)
@Schema(description = "创建用户")
public class CreateUserRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "明文密码（服务端 BCrypt 存储，不回显）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "昵称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    @Size(max = 512)
    private String avatar;

    @Size(max = 32)
    private String languageCode;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;

    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;
}
