package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysBlacklistProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 访问黑名单实体（对齐 schema v11 {@code sys_blacklist}）。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_blacklist")
@EntityProxy
public class SysBlacklist extends BaseEntity implements ProxyEntityAvailable<SysBlacklist, SysBlacklistProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** IP / USER / DEVICE */
    private String targetType;

    /** 目标值（IP 文本；USER=用户 id 字符串；DEVICE=deviceId）。 */
    private String targetValue;

    /** LOGIN / API / ALL */
    private String scope;

    /** 封禁原因（审计可见；可空串）。 */
    private String reason;

    /** 生效开始（含）。 */
    private LocalDateTime startsAt;

    /** 生效结束（不含）；null=永不过期。 */
    private LocalDateTime expiresAt;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
