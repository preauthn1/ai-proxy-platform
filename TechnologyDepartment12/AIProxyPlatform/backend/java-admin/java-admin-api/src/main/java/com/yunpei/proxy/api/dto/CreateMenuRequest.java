package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.menu.MenuManageModels.CreateMenuCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建菜单请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateMenuCommand.class)
@Schema(description = "创建菜单")
public class CreateMenuRequest {

    @Schema(description = "父菜单 ID；null=根")
    private Long parentId;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "菜单名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Size(max = 16)
    @Schema(description = "DIR | MENU | BUTTON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Size(max = 255)
    @Schema(description = "路由 path（MENU 必填）")
    private String path;

    @Size(max = 255)
    @Schema(description = "组件路径（仅 MENU）")
    private String component;

    @Size(max = 64)
    private String icon;

    @Size(max = 255)
    private String redirect;

    @Size(max = 128)
    @Schema(description = "权限码（BUTTON 必填）")
    private String permissionCode;

    @Schema(description = "前端扩展 JSON 字符串")
    private String metadata;

    private Integer sort;

    @Schema(description = "0 显示 / 1 隐藏")
    private Integer isHidden;

    @Schema(description = "1 启用 / 0 禁用")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;
}
