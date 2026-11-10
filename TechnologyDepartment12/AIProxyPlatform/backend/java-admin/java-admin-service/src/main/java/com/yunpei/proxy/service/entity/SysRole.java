package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysRoleProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体（对齐 schema v10 {@code sys_role}）。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_role")
@EntityProxy
public class SysRole extends BaseEntity implements ProxyEntityAvailable<SysRole, SysRoleProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String code;

    private String name;

    private Long parentId;

    private Integer sort;

    private String remark;

    private Integer isEnabled;
}
