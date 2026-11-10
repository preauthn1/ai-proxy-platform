package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.DictTypeProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型实体（对齐 schema v10 {@code dict_type}）。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("dict_type")
@EntityProxy
public class DictType extends BaseEntity implements ProxyEntityAvailable<DictType, DictTypeProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 字典类型编码（如 user_status）。 */
    private String code;

    /** 展示名。 */
    private String name;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
