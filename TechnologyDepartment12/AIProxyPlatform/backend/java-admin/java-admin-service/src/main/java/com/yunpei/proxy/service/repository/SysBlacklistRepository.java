package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.SysBlacklist;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 访问黑名单 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysBlacklistRepository {

    private final EasyEntityQuery easyEntityQuery;

    public SysBlacklist findById(Long id) {
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public EasyPageResult<SysBlacklist> page(
            int page, int pageSize, String targetType, String targetValueLike, String scope, Integer status) {
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> {
                    t.targetType().eq(targetType != null, targetType);
                    t.targetValue().like(targetValueLike != null, targetValueLike);
                    t.scope().eq(scope != null, scope);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<SysBlacklist> listFiltered(String targetType, String targetValueLike, String scope, Integer status) {
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> {
                    t.targetType().eq(targetType != null, targetType);
                    t.targetValue().like(targetValueLike != null, targetValueLike);
                    t.scope().eq(scope != null, scope);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public List<SysBlacklist> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> t.id().in(ids))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /**
     * 同 (target_type, target_value, scope, starts_at, expires_at) 活跃行是否已存在。
     *
     * <p>补齐 MySQL UNIQUE 对 {@code expires_at IS NULL} 的 NULL 不等语义漏洞。
     */
    public boolean existsExactWindow(
            String targetType,
            String targetValue,
            String scope,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Long excludeId) {
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> {
                    t.targetType().eq(targetType);
                    t.targetValue().eq(targetValue);
                    t.scope().eq(scope);
                    t.startsAt().eq(startsAt);
                    if (expiresAt == null) {
                        t.expiresAt().isNull();
                    } else {
                        t.expiresAt().eq(expiresAt);
                    }
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    /**
     * 运行时命中判定（S1）：启用 + 时间窗内 + scope 覆盖请求场景。
     *
     * <p>多行 OR：任意一条命中即返回（LIMIT 1）。软删行由 {@code @LogicDelete} 自动排除。
     * DEVICE 调用方可不查。
     *
     * @return 命中行（含 reason 供服务端日志）；未命中返回 {@code null}
     */
    public SysBlacklist findActiveHit(String targetType, String targetValue, String requestScope, LocalDateTime now) {
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> {
                    t.targetType().eq(targetType);
                    t.targetValue().eq(targetValue);
                    t.scope().in(List.of(requestScope, "ALL"));
                    t.isEnabled().eq(1);
                    t.startsAt().le(now);
                    t.or(() -> {
                        t.expiresAt().isNull();
                        t.expiresAt().gt(now);
                    });
                })
                .firstOrNull();
    }

    /** 是否存在生效命中（多行 OR）。 */
    public boolean existsActiveHit(String targetType, String targetValue, String requestScope, LocalDateTime now) {
        return findActiveHit(targetType, targetValue, requestScope, now) != null;
    }

    /**
     * 时间窗区间重叠查询（可选 soft warning 用；不拒绝创建）。
     *
     * <p>重叠条件：{@code starts_at < other.expires} 且 {@code expires > other.starts}（null expires 视为 +∞）。
     */
    public boolean existsOverlappingWindow(
            String targetType,
            String targetValue,
            String scope,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Long excludeId) {
        return easyEntityQuery
                .queryable(SysBlacklist.class)
                .where(t -> {
                    t.targetType().eq(targetType);
                    t.targetValue().eq(targetValue);
                    t.scope().eq(scope);
                    t.id().ne(excludeId != null, excludeId);
                    // existing.starts < new.expires（new.expires null = +∞）
                    if (expiresAt != null) {
                        t.startsAt().lt(expiresAt);
                    }
                    // existing.expires > new.starts 或 existing.expires is null
                    t.or(() -> {
                        t.expiresAt().isNull();
                        t.expiresAt().gt(startsAt);
                    });
                })
                .any();
    }

    public void insert(SysBlacklist row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(SysBlacklist row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(SysBlacklist.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(SysBlacklist.class)
                .setColumns(t -> t.isEnabled().set(isEnabled))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
