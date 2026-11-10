package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysLoginLogProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 登录日志实体（只增不改；对齐 schema v10 {@code sys_login_log}）。
 *
 * <p>非核心业务表：无软删 / 无 updated_at / 无 created_by，故不继承 {@link BaseEntity}。
 *
 * @author yunpei
 */
@Data
@Table("sys_login_log")
@EntityProxy
public class SysLoginLog implements ProxyEntityAvailable<SysLoginLog, SysLoginLogProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String username;

    /** 1=成功 0=失败 */
    private Integer success;

    private String reason;

    private Integer statusCode;

    private Long sysUserId;

    /** PASSWORD / SSO / OAUTH / SMS */
    private String loginMethod;

    private LocalDateTime loginTime;

    private String loginIp;

    private String loginMac;

    private String clientId;

    private String clientName;

    private String userAgent;

    private String browserName;

    private String browserVersion;

    private String osName;

    private String osVersion;

    private String location;

    private LocalDateTime createdAt;
}
