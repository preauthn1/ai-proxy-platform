package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysLoginLogArchiveProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 登录日志归档实体（对齐 schema v10 {@code sys_login_log_archive}）。
 *
 * @author yunpei
 */
@Data
@Table("sys_login_log_archive")
@EntityProxy
public class SysLoginLogArchive implements ProxyEntityAvailable<SysLoginLogArchive, SysLoginLogArchiveProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String username;

    /** 1=成功 0=失败 */
    private Integer success;

    private String reason;

    private Integer statusCode;

    private Long sysUserId;

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

    private LocalDateTime archivedAt;
}
