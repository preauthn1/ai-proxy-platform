package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.DictType;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 字典类型 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class DictTypeRepository {

    private final EasyEntityQuery easyEntityQuery;

    public DictType findById(Long id) {
        return easyEntityQuery
                .queryable(DictType.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public DictType findByCode(String code) {
        return easyEntityQuery
                .queryable(DictType.class)
                .where(t -> t.code().eq(code))
                .firstOrNull();
    }

    public boolean existsByCode(String code, Long excludeId) {
        return easyEntityQuery
                .queryable(DictType.class)
                .where(t -> {
                    t.code().eq(code);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public EasyPageResult<DictType> page(
            int page, int pageSize, List<String> codeExact, String codeLike, String name, Integer status) {
        return easyEntityQuery
                .queryable(DictType.class)
                .where(t -> {
                    if (codeExact != null && !codeExact.isEmpty()) {
                        t.code().in(codeExact);
                    } else if (codeLike != null) {
                        t.code().like(codeLike);
                    }
                    t.name().like(name != null, name);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> t.id().asc())
                .toPageResult(page, pageSize);
    }

    public List<DictType> listFiltered(List<String> codeExact, String codeLike, String name, Integer status) {
        return easyEntityQuery
                .queryable(DictType.class)
                .where(t -> {
                    if (codeExact != null && !codeExact.isEmpty()) {
                        t.code().in(codeExact);
                    } else if (codeLike != null) {
                        t.code().like(codeLike);
                    }
                    t.name().like(name != null, name);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public List<DictType> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(DictType.class)
                .where(t -> t.id().in(ids))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /** 按 code 模糊匹配，返回命中类型 id（用于 dict-data typeCode 单值过滤）。 */
    public List<Long> findIdsByCodeContains(String codeFragment) {
        return easyEntityQuery.queryable(DictType.class).where(t -> t.code().like(codeFragment)).toList().stream()
                .map(DictType::getId)
                .toList();
    }

    /** 按 code 精确匹配任一，返回命中类型 id。 */
    public List<Long> findIdsByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery.queryable(DictType.class).where(t -> t.code().in(codes)).toList().stream()
                .map(DictType::getId)
                .toList();
    }

    public void insert(DictType type) {
        easyEntityQuery.insertable(type).executeRows(true);
    }

    public long update(DictType type) {
        return easyEntityQuery.updatable(type).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(DictType.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(DictType.class)
                .setColumns(t -> t.isEnabled().set(isEnabled))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
