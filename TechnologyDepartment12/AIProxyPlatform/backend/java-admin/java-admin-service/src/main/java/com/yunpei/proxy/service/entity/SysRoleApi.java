package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysRoleApiProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色-API 关联（复合主键；无软删）。
 *
 * @author yunpei
 */
@Data
@Table("sys_role_api")
@EntityProxy
public class SysRoleApi implements ProxyEntityAvailable<SysRoleApi, SysRoleApiProxy> {

    @Column(primaryKey = true)
    private Long roleId;

    @Column(primaryKey = true)
    private Long apiId;

    private LocalDateTime createdAt;
}
