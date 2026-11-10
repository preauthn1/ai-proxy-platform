package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新角色请求（code 不可改）。
 *
 * <p>通过 {@link #parentIdPresent} 区分 JSON 省略 parentId 与显式 null。
 *
 * @author yunpei
 */
@Data
@Schema(description = "更新角色")
public class UpdateRoleRequest {

    @Size(max = 64)
    private String name;

    @Schema(description = "父角色 ID；显式 null=清除父角色；省略=不改")
    private Long parentId;

    /** 请求体是否出现 parentId 字段。 */
    @JsonIgnore
    private boolean parentIdPresent;

    private Integer sort;

    @Schema(description = "1=启用 0=禁用")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;

    @JsonSetter("parentId")
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdPresent = true;
    }
}
