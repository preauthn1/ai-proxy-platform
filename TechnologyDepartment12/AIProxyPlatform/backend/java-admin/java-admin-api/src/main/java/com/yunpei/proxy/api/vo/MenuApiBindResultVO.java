package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单-API 绑定结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = MenuApiBindResult.class)
@Schema(description = "菜单 API 绑定结果")
public class MenuApiBindResultVO {

    @Schema(description = "菜单 ID")
    private Long menuId;

    @Schema(description = "绑定的 API ID 列表")
    private List<Long> apiIds;
}
