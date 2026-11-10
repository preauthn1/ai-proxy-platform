package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.proxy.SysApiProxy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统 API 资源 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysApiRepository {

    private final EasyEntityQuery easyEntityQuery;

    public SysApi findById(Long id) {
        return easyEntityQuery.queryable(SysApi.class).where(a -> a.id().eq(id)).firstOrNull();
    }

    /** 全量未软删 API，按 id 升序。 */
    public List<SysApi> listAll() {
        return easyEntityQuery
                .queryable(SysApi.class)
                .orderBy(a -> a.id().asc())
                .toList();
    }

    /**
     * 列表筛选（未软删）；在内存中再按分组分页。
     *
     * @param name   名称模糊；null 不过滤
     * @param path   路径模糊；null 不过滤
     * @param method HTTP method 精确；null 不过滤
     * @param group  api_group 精确；null 不过滤
     * @param status is_enabled；null 不过滤
     */
    public List<SysApi> listFiltered(String name, String path, String method, String group, Integer status) {
        return easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> {
                    a.name().like(name != null, name);
                    a.path().like(path != null, path);
                    a.method().eq(method != null, method);
                    a.apiGroup().eq(group != null, group);
                    a.isEnabled().eq(status != null, status);
                })
                .orderBy(a -> a.id().asc())
                .toList();
    }

    /** (method, path) 是否已被其他未软删行占用。 */
    public boolean existsByMethodAndPath(String method, String path, Long excludeId) {
        return easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> {
                    a.method().eq(method);
                    a.path().eq(path);
                    a.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    /** permission_code 是否已被其他未软删行占用。 */
    public boolean existsByPermissionCode(String permissionCode, Long excludeId) {
        return easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> {
                    a.permissionCode().eq(permissionCode);
                    a.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public List<SysApi> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> a.id().in(ids))
                .orderBy(a -> a.id().asc())
                .toList();
    }

    public void insert(SysApi api) {
        easyEntityQuery.insertable(api).executeRows(true);
    }

    public long update(SysApi api) {
        return easyEntityQuery.updatable(api).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery.deletable(SysApi.class).where(a -> a.id().eq(id)).executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(SysApi.class)
                .setColumns(a -> a.isEnabled().set(isEnabled))
                .where(a -> a.id().eq(id))
                .executeRows();
    }

    /** 去重的非空 api_group（未软删），字典序。 */
    public List<String> listDistinctGroups() {
        List<String> groups = easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> {
                    a.apiGroup().isNotNull();
                    a.apiGroup().ne("");
                })
                .select(SysApiProxy::apiGroup)
                .distinct()
                .toList();
        return groups.stream().sorted(String::compareTo).toList();
    }
}
