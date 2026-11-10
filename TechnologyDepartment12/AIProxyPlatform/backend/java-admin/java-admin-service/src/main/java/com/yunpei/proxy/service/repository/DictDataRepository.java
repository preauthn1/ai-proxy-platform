package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.DictData;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 字典数据 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class DictDataRepository {

    private final EasyEntityQuery easyEntityQuery;

    public DictData findById(Long id) {
        return easyEntityQuery
                .queryable(DictData.class)
                .where(d -> d.id().eq(id))
                .firstOrNull();
    }

    /**
     * 唯一键冲突检测：{@code (type_id, value, platform)} 在未软删行中是否被占用。
     */
    public boolean existsByTypeValuePlatform(Long typeId, String value, String platform, Long excludeId) {
        return easyEntityQuery
                .queryable(DictData.class)
                .where(d -> {
                    d.typeId().eq(typeId);
                    d.value().eq(value);
                    d.platform().eq(platform);
                    d.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public boolean existsActiveByTypeId(Long typeId) {
        return easyEntityQuery
                .queryable(DictData.class)
                .where(d -> d.typeId().eq(typeId))
                .any();
    }

    public EasyPageResult<DictData> page(
            int page,
            int pageSize,
            Long typeId,
            Collection<Long> typeIds,
            String label,
            String value,
            Integer status,
            String platform,
            boolean includeGeneral) {
        return easyEntityQuery
                .queryable(DictData.class)
                .where(d -> {
                    d.typeId().eq(typeId != null, typeId);
                    if (typeIds != null) {
                        if (typeIds.isEmpty()) {
                            d.id().eq(-1L);
                        } else {
                            d.typeId().in(typeIds);
                        }
                    }
                    d.label().like(label != null, label);
                    d.value().like(value != null, value);
                    d.isEnabled().eq(status != null, status);
                    if (platform != null && !platform.isEmpty()) {
                        if ("general".equals(platform) || !includeGeneral) {
                            d.platform().eq(platform);
                        } else {
                            d.platform().in(List.of(platform, "general"));
                        }
                    }
                })
                .orderBy(d -> {
                    d.sort().asc();
                    d.id().asc();
                })
                .toPageResult(page, pageSize);
    }

    /** 按类型取启用项（by-type 下拉）。 */
    public List<DictData> listEnabledByTypeId(Long typeId) {
        return easyEntityQuery
                .queryable(DictData.class)
                .where(d -> {
                    d.typeId().eq(typeId);
                    d.isEnabled().eq(1);
                })
                .orderBy(d -> {
                    d.sort().asc();
                    d.id().asc();
                })
                .toList();
    }

    public List<DictData> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(DictData.class)
                .where(d -> d.id().in(ids))
                .orderBy(d -> d.id().asc())
                .toList();
    }

    public void insert(DictData data) {
        easyEntityQuery.insertable(data).executeRows(true);
    }

    public long update(DictData data) {
        return easyEntityQuery.updatable(data).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(DictData.class)
                .where(d -> d.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(DictData.class)
                .setColumns(d -> d.isEnabled().set(isEnabled))
                .where(d -> d.id().eq(id))
                .executeRows();
    }
}
