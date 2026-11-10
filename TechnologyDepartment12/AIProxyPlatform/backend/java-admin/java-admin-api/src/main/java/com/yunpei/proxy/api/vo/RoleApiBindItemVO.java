package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色可授权接口项（带 bound）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = RoleApiBindView.class)
@Schema(description = "角色 API 绑定项")
public class RoleApiBindItemVO {

    private Long id;
    private String name;
    private String method;
    private String path;
    private String permissionCode;
    private String apiGroup;
    private Integer isEnabled;
    private boolean bound;
}
