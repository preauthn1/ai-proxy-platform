package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.I18nLocaleProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * I18n 语言/区域实体（对齐 schema v10 {@code i18n_locale}）。
 *
 * <p>唯一键：{@code (code, deleted_at)}；应用层保证最多一条 {@code is_default=1}。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("i18n_locale")
@EntityProxy
public class I18nLocale extends BaseEntity implements ProxyEntityAvailable<I18nLocale, I18nLocaleProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 语言/区域代码（如 zh-CN / en-US）。 */
    private String code;

    /** 展示名。 */
    private String name;

    /** 是否默认语言（应用层保证最多一条）。 */
    private Integer isDefault;

    private Integer sort;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
