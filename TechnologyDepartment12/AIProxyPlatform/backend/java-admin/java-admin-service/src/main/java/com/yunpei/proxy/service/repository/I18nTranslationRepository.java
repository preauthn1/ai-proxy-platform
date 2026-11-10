package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.I18nTranslation;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * i18n_translation Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class I18nTranslationRepository {

    private final EasyEntityQuery easyEntityQuery;

    public I18nTranslation findById(Long id) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public boolean existsByLocaleAndKey(Long localeId, String translationKey, Long excludeId) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> {
                    t.localeId().eq(localeId);
                    t.translationKey().eq(translationKey);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public boolean existsActiveByLocaleId(Long localeId) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> t.localeId().eq(localeId))
                .any();
    }

    public I18nTranslation findByLocaleAndKey(Long localeId, String translationKey) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> {
                    t.localeId().eq(localeId);
                    t.translationKey().eq(translationKey);
                })
                .firstOrNull();
    }

    public List<I18nTranslation> listByTranslationKey(String translationKey) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> t.translationKey().eq(translationKey))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public List<I18nTranslation> listEnabledByLocaleId(Long localeId) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> {
                    t.localeId().eq(localeId);
                    t.isEnabled().eq(1);
                })
                .orderBy(t -> t.translationKey().asc())
                .toList();
    }

    public List<I18nTranslation> listByLocaleIds(Collection<Long> localeIds) {
        if (localeIds == null || localeIds.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> t.localeId().in(localeIds))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public List<I18nTranslation> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> t.id().in(ids))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /**
     * 分页查询。
     *
     * <p>{@code value} 对 translation_key 与 value 做 OR 模糊；实现为两次 like 或内存过滤。
     * 此处用 SQL 侧 OR。
     */
    public EasyPageResult<I18nTranslation> page(int page, int pageSize, Long localeId, String value, Integer status) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> {
                    t.localeId().eq(localeId != null, localeId);
                    t.isEnabled().eq(status != null, status);
                    if (value != null && !value.isEmpty()) {
                        t.or(() -> {
                            t.translationKey().like(value);
                            t.value().like(value);
                        });
                    }
                })
                .orderBy(t -> t.id().asc())
                .toPageResult(page, pageSize);
    }

    /** 全量过滤（byKey 聚合前拉取，数据量通常可控）。 */
    public List<I18nTranslation> listFiltered(Long localeId, String value, Integer status) {
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> {
                    t.localeId().eq(localeId != null, localeId);
                    t.isEnabled().eq(status != null, status);
                    if (value != null && !value.isEmpty()) {
                        t.or(() -> {
                            t.translationKey().like(value);
                            t.value().like(value);
                        });
                    }
                })
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /**
     * 导入预览：按 locale_id 集合 + key 集合过滤。
     */
    public List<I18nTranslation> listByLocaleIdsAndKeys(Collection<Long> localeIds, Collection<String> keys) {
        if (localeIds == null || localeIds.isEmpty() || keys == null || keys.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(I18nTranslation.class)
                .where(t -> {
                    t.localeId().in(localeIds);
                    t.translationKey().in(keys);
                })
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public void insert(I18nTranslation row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(I18nTranslation row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(I18nTranslation.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(I18nTranslation.class)
                .setColumns(t -> t.isEnabled().set(isEnabled))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long renameKey(String fromKey, String toKey) {
        return easyEntityQuery
                .updatable(I18nTranslation.class)
                .setColumns(t -> t.translationKey().set(toKey))
                .where(t -> t.translationKey().eq(fromKey))
                .executeRows();
    }
}
