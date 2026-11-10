package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.user.UserManageModels.UpdateUserCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 更新用户请求（username/password 不可改）。
 *
 * <p>映射到 {@link UpdateUserCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateUserCommand.class)
@Schema(description = "更新用户")
public class UpdateUserRequest {

    @Size(max = 64)
    private String nickname;

    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    @Size(max = 512)
    private String avatar;

    @Size(max = 32)
    private String languageCode;

    @Schema(description = "1=启用 0=禁用")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;

    @Schema(description = "角色 ID 列表；省略则不改角色，传空数组清空角色")
    private List<Long> roleIds;
}
