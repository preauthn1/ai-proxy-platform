package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色-菜单绑定结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = RoleMenuBindResult.class)
@Schema(description = "角色菜单绑定结果")
public class RoleMenuBindResultVO {

    @Schema(description = "角色 ID")
    private Long roleId;

    @Schema(description = "绑定的菜单 ID 列表")
    private List<Long> menuIds;
}
