package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.SysUserProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体。
 *
 * <p>对齐 {@code backend/db} schema v10（{@code backend/db/docs/db-conventions.md}）：
 * <ul>
 *     <li>表名：{@code sys_user}</li>
 *     <li>主键：{@code BIGINT UNSIGNED} → {@code Long}</li>
 *     <li>密码列：{@code password_hash}（禁止回显到 API）</li>
 *     <li>启停：{@code is_enabled}；软删/审计见 {@link BaseEntity}</li>
 * </ul>
 *
 * <p>Easy-Query {@code name-conversion: underlined} 将 camelCase 字段映射为 snake_case 列。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_user")
@EntityProxy
public class SysUser extends BaseEntity implements ProxyEntityAvailable<SysUser, SysUserProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String username;

    /** 密码哈希（bcrypt）；禁止序列化到对外 VO。 */
    private String passwordHash;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    /** 用户默认语言码（软外键 → i18n_locale.code；可为 null）。 */
    private String languageCode;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    /** 1=启用 0=禁用（与 deleted_at 构成三态）。 */
    private Integer isEnabled;
}
