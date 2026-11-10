package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.menu.MenuManageModels.MenuView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单列表/详情 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = MenuView.class)
@Schema(description = "菜单")
public class MenuListItemVO {

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
    private Long deletedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
