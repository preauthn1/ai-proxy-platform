package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysRoleMenuProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色-菜单关联（复合主键；无软删）。
 *
 * @author yunpei
 */
@Data
@Table("sys_role_menu")
@EntityProxy
public class SysRoleMenu implements ProxyEntityAvailable<SysRoleMenu, SysRoleMenuProxy> {

    @Column(primaryKey = true)
    private Long roleId;

    @Column(primaryKey = true)
    private Long menuId;

    private LocalDateTime createdAt;
}
