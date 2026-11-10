package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysUserRoleProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户-角色关联（复合主键；无软删）。
 *
 * @author yunpei
 */
@Data
@Table("sys_user_role")
@EntityProxy
public class SysUserRole implements ProxyEntityAvailable<SysUserRole, SysUserRoleProxy> {

    @Column(primaryKey = true)
    private Long userId;

    @Column(primaryKey = true)
    private Long roleId;

    private LocalDateTime createdAt;
}
