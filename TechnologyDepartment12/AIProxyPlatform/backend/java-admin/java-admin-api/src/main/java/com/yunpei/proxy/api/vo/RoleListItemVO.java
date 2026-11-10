package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.role.RoleManageModels.RoleView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色列表/详情 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = RoleView.class)
@Schema(description = "角色信息")
public class RoleListItemVO {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private Integer sort;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    /** 角色下未软删用户数 */
    private Long userCount;
    /** 父角色名（无父则为 null） */
    private String parentName;
}
