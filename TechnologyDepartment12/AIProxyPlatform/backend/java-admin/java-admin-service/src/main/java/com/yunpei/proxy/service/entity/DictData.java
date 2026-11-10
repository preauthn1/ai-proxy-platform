package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.DictDataProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据项实体（对齐 schema v10 {@code dict_data}）。
 *
 * <p>唯一键：{@code (type_id, value, platform, deleted_at)}。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("dict_data")
@EntityProxy
public class DictData extends BaseEntity implements ProxyEntityAvailable<DictData, DictDataProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所属字典类型 id。 */
    private Long typeId;

    /** 字典值。 */
    private String value;

    /** 展示标签。 */
    private String label;

    /** 同类型内排序。 */
    private Integer sort;

    /** 是否该类型默认值。 */
    private Integer isDefault;

    /** 归属平台：general、react-admin、vue-admin。 */
    private String platform;

    /**
     * 预设样式标识（antd Tag color 子集）。
     */
    private String tagType;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
