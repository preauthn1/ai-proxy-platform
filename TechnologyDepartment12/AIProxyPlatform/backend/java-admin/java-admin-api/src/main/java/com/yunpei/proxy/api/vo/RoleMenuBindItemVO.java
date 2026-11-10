package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色可授权菜单项（带 bound）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = RoleMenuBindView.class)
@Schema(description = "角色菜单绑定项")
public class RoleMenuBindItemVO {

    private Long id;
    private Long parentId;
    private String name;
    private String type;
    private String path;
    private String component;
    private String icon;
    private String redirect;
    private String permissionCode;
    private String treePath;
    private String metadata;
    private Integer sort;
    private Integer isHidden;
    private Integer isEnabled;
    private String remark;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean bound;
}
