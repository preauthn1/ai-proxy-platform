package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单绑定 API 列表项（含 bound 标记）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = MenuApiBindView.class)
@Schema(description = "菜单-API 绑定项")
public class MenuApiBindItemVO {

    private Long id;
    private String name;
    private String method;
    private String path;
    private String permissionCode;
    private String apiGroup;
    private Integer isEnabled;
    private boolean bound;
}
