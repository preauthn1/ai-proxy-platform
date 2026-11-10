package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.I18nLocale;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * i18n_locale Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class I18nLocaleRepository {

    private final EasyEntityQuery easyEntityQuery;

    public I18nLocale findById(Long id) {
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public I18nLocale findByCode(String code) {
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .where(t -> t.code().eq(code))
                .firstOrNull();
    }

    public boolean existsByCode(String code, Long excludeId) {
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .where(t -> {
                    t.code().eq(code);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public EasyPageResult<I18nLocale> page(
            int page, int pageSize, List<String> codeExact, String codeLike, String name, Integer status) {
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .where(t -> {
                    if (codeExact != null && !codeExact.isEmpty()) {
                        t.code().in(codeExact);
                    } else if (codeLike != null) {
                        t.code().like(codeLike);
                    }
                    t.name().like(name != null, name);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> {
                    t.sort().asc();
                    t.id().asc();
                })
                .toPageResult(page, pageSize);
    }

    public List<I18nLocale> listFiltered(List<String> codeExact, String codeLike, String name, Integer status) {
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .where(t -> {
                    if (codeExact != null && !codeExact.isEmpty()) {
                        t.code().in(codeExact);
                    } else if (codeLike != null) {
                        t.code().like(codeLike);
                    }
                    t.name().like(name != null, name);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> {
                    t.sort().asc();
                    t.id().asc();
                })
                .toList();
    }

    public List<I18nLocale> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .where(t -> t.id().in(ids))
                .orderBy(t -> {
                    t.sort().asc();
                    t.id().asc();
                })
                .toList();
    }

    public List<I18nLocale> listAllActive() {
        return easyEntityQuery
                .queryable(I18nLocale.class)
                .orderBy(t -> {
                    t.sort().asc();
                    t.id().asc();
                })
                .toList();
    }

    public void insert(I18nLocale locale) {
        easyEntityQuery.insertable(locale).executeRows(true);
    }

    public long update(I18nLocale locale) {
        return easyEntityQuery.updatable(locale).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(I18nLocale.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(I18nLocale.class)
                .setColumns(t -> t.isEnabled().set(isEnabled))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    /** 清除其它语言的 is_default（excludeId 保留）。 */
    public long clearDefaultExcept(Long excludeId) {
        return easyEntityQuery
                .updatable(I18nLocale.class)
                .setColumns(t -> t.isDefault().set(0))
                .where(t -> {
                    t.isDefault().eq(1);
                    t.id().ne(excludeId != null, excludeId);
                })
                .executeRows();
    }
}
