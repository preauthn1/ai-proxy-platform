package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.SysMenuApi;
import com.yunpei.proxy.service.entity.SysRoleMenu;
import com.yunpei.proxy.service.entity.proxy.SysApiProxy;
import com.yunpei.proxy.service.entity.proxy.SysMenuApiProxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 菜单-API 快捷绑定仓储（关联表无软删：硬删再插）。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysMenuApiRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<Long> findApiIdsByMenuId(Long menuId) {
        return easyEntityQuery
                .queryable(SysMenuApi.class)
                .where(ma -> ma.menuId().eq(menuId))
                .select(SysMenuApiProxy::apiId)
                .toList();
    }

    /**
     * 按菜单 ID 聚合绑定的未软删 apiId（去重排序）。
     */
    public List<Long> findDistinctApiIdsByMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        List<Long> apiIds = easyEntityQuery
                .queryable(SysMenuApi.class)
                .where(ma -> ma.menuId().in(menuIds))
                .select(SysMenuApiProxy::apiId)
                .distinct()
                .toList();
        if (apiIds.isEmpty()) {
            return List.of();
        }
        // 过滤已软删 API
        List<Long> valid = easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> a.id().in(apiIds))
                .select(SysApiProxy::id)
                .toList();
        return valid.stream().sorted().toList();
    }

    /** 全量未软删 API。 */
    public List<SysApi> listAllApis() {
        return easyEntityQuery
                .queryable(SysApi.class)
                .orderBy(a -> a.id().asc())
                .toList();
    }

    /**
     * 保留存在且未软删的 API ID（去重保序）；非法 ID 静默丢弃（对齐 mock setMenuApis）。
     */
    public List<Long> retainExistingApiIds(List<Long> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = apiIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        Set<Long> found = new HashSet<>(easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> a.id().in(distinct))
                .select(SysApiProxy::id)
                .toList());
        List<Long> retained = new ArrayList<>();
        for (Long id : distinct) {
            if (found.contains(id)) {
                retained.add(id);
            }
        }
        return retained;
    }

    /** 全量替换某菜单的 API 绑定。返回最终绑定列表。 */
    public List<Long> replaceApis(Long menuId, List<Long> apiIds) {
        clearByMenuId(menuId);
        if (apiIds == null || apiIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = TimeZones.now();
        List<SysMenuApi> rows = new ArrayList<>(apiIds.size());
        for (Long apiId : apiIds) {
            SysMenuApi row = new SysMenuApi();
            row.setMenuId(menuId);
            row.setApiId(apiId);
            row.setCreatedAt(now);
            row.setCreatedBy(0L);
            rows.add(row);
        }
        easyEntityQuery.insertable(rows).executeRows();
        return List.copyOf(apiIds);
    }

    public void clearByMenuId(Long menuId) {
        easyEntityQuery
                .deletable(SysMenuApi.class)
                .where(ma -> ma.menuId().eq(menuId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    /** API 软删前清除所有菜单对该 API 的绑定。 */
    public void clearByApiId(Long apiId) {
        easyEntityQuery
                .deletable(SysMenuApi.class)
                .where(ma -> ma.apiId().eq(apiId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    /** 菜单软删前清除所有角色对该菜单的绑定。 */
    public void clearRoleMenusByMenuId(Long menuId) {
        easyEntityQuery
                .deletable(SysRoleMenu.class)
                .where(rm -> rm.menuId().eq(menuId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    public Set<Long> boundApiIdSet(Long menuId) {
        return new HashSet<>(findApiIdsByMenuId(menuId));
    }
}
