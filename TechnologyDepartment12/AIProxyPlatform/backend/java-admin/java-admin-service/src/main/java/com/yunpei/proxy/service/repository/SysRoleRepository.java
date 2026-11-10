package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.SysRole;
import com.yunpei.proxy.service.role.RoleManageModels.RoleListQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统角色 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 LogicDelete 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysRoleRepository {

    private final EasyEntityQuery easyEntityQuery;

    public SysRole findById(Long id) {
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> r.id().eq(id))
                .firstOrNull();
    }

    public boolean existsByCode(String code) {
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> r.code().eq(code))
                .any();
    }

    public boolean existsById(Long id) {
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> r.id().eq(id))
                .any();
    }

    /**
     * 分页；筛选 code/name 模糊、status；按 sort/id 升序。
     */
    public EasyPageResult<SysRole> page(RoleListQuery query) {
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> {
                    r.code().like(query.code() != null, query.code());
                    r.name().like(query.name() != null, query.name());
                    r.isEnabled().eq(query.status() != null, query.status());
                })
                .orderBy(r -> {
                    r.sort().asc();
                    r.id().asc();
                })
                .toPageResult(query.page(), query.pageSize());
    }

    /**
     * 全量未软删角色；可选 status；按 sort/id 升序。
     */
    public List<SysRole> listAll(Integer status) {
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> r.isEnabled().eq(status != null, status))
                .orderBy(r -> {
                    r.sort().asc();
                    r.id().asc();
                })
                .toList();
    }

    public void insert(SysRole role) {
        easyEntityQuery.insertable(role).executeRows(true);
    }

    public long update(SysRole role) {
        return easyEntityQuery.updatable(role).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(SysRole.class)
                .where(r -> r.id().eq(id))
                .executeRows();
    }

    /** 是否存在未软删子角色。 */
    public boolean hasChildren(Long parentId) {
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> r.parentId().eq(parentId))
                .any();
    }

    public Map<Long, String> findNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return easyEntityQuery.queryable(SysRole.class).where(r -> r.id().in(ids)).toList().stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a, LinkedHashMap::new));
    }
}
