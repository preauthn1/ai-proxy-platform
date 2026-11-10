package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysApiProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API 资源实体（对齐 schema v10 {@code sys_api}）。
 *
 * <p>本模块主要用于用户-角色变更时展开 Casbin 策略（path + method）。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_api")
@EntityProxy
public class SysApi extends BaseEntity implements ProxyEntityAvailable<SysApi, SysApiProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String name;

    private String method;

    private String path;

    private String permissionCode;

    private String apiGroup;

    private String remark;

    private Integer isEnabled;
}
