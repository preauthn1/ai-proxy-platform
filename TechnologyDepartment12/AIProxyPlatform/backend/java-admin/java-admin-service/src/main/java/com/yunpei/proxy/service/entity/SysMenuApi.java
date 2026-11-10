package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysMenuApiProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 菜单-API 快捷绑定（复合主键；无软删，解绑=硬删行）。
 *
 * <p>非授权关系，仅用于「按菜单批量赋权」结构化绑定。
 *
 * @author yunpei
 */
@Data
@Table("sys_menu_api")
@EntityProxy
public class SysMenuApi implements ProxyEntityAvailable<SysMenuApi, SysMenuApiProxy> {

    @Column(primaryKey = true)
    private Long menuId;

    @Column(primaryKey = true)
    private Long apiId;

    private LocalDateTime createdAt;

    private Long createdBy;
}
