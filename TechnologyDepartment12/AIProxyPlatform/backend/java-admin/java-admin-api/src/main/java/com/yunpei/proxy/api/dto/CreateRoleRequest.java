package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.role.RoleManageModels.CreateRoleCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建角色请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateRoleCommand.class)
@Schema(description = "创建角色")
public class CreateRoleRequest {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "角色编码（创建后不可改）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "角色名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "父角色 ID；null/省略=无父")
    private Long parentId;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;
}
