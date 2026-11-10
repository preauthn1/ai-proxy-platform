package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.I18nTranslationProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * I18n 翻译实体（对齐 schema v10 {@code i18n_translation}）。
 *
 * <p>唯一键：{@code (locale_id, translation_key, deleted_at)}。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("i18n_translation")
@EntityProxy
public class I18nTranslation extends BaseEntity implements ProxyEntityAvailable<I18nTranslation, I18nTranslationProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所属语言 id。 */
    private Long localeId;

    /** 翻译键（如 menu.user.create）。 */
    private String translationKey;

    /** 翻译值。 */
    private String value;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
