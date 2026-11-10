package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.entity.SysRoleApi;
import com.yunpei.proxy.service.entity.SysRoleMenu;
import com.yunpei.proxy.service.entity.proxy.SysApiProxy;
import com.yunpei.proxy.service.entity.proxy.SysMenuProxy;
import com.yunpei.proxy.service.entity.proxy.SysRoleApiProxy;
import com.yunpei.proxy.service.entity.proxy.SysRoleMenuProxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 角色-菜单 / 角色-API 绑定仓储（关联表无软删：硬删再插）。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysRoleBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<Long> findMenuIdsByRoleId(Long roleId) {
        return easyEntityQuery
                .queryable(SysRoleMenu.class)
                .where(rm -> rm.roleId().eq(roleId))
                .select(SysRoleMenuProxy::menuId)
                .toList();
    }

    public List<Long> findApiIdsByRoleId(Long roleId) {
        return easyEntityQuery
                .queryable(SysRoleApi.class)
                .where(ra -> ra.roleId().eq(roleId))
                .select(SysRoleApiProxy::apiId)
                .toList();
    }

    /** 全量未软删菜单，按 sort/id 升序。 */
    public List<SysMenu> listAllMenus() {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .orderBy(m -> {
                    m.sort().asc();
                    m.id().asc();
                })
                .toList();
    }

    /** 全量未软删 API，按 id 升序。 */
    public List<SysApi> listAllApis() {
        return easyEntityQuery
                .queryable(SysApi.class)
                .orderBy(a -> a.id().asc())
                .toList();
    }

    /**
     * 校验菜单均存在且未软删；返回去重保序 ID。
     *
     * @return null 表示有非法 ID
     */
    public List<Long> filterExistingMenuIds(List<Long> menuIds) {
        return filterExistingIds(menuIds, SysMenu.class);
    }

    /**
     * 校验 API 均存在且未软删；返回去重保序 ID。
     *
     * @return null 表示有非法 ID
     */
    public List<Long> filterExistingApiIds(List<Long> apiIds) {
        return filterExistingIds(apiIds, SysApi.class);
    }

    private List<Long> filterExistingIds(List<Long> ids, Class<?> entityClass) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Long> found;
        if (entityClass == SysMenu.class) {
            found = easyEntityQuery
                    .queryable(SysMenu.class)
                    .where(m -> m.id().in(distinct))
                    .select(SysMenuProxy::id)
                    .toList();
        } else {
            found = easyEntityQuery
                    .queryable(SysApi.class)
                    .where(a -> a.id().in(distinct))
                    .select(SysApiProxy::id)
                    .toList();
        }
        if (found.size() != distinct.size()) {
            return null;
        }
        return distinct;
    }

    public void replaceMenus(Long roleId, List<Long> menuIds) {
        clearMenus(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        LocalDateTime now = TimeZones.now();
        List<SysRoleMenu> rows = new ArrayList<>(menuIds.size());
        for (Long menuId : menuIds) {
            SysRoleMenu row = new SysRoleMenu();
            row.setRoleId(roleId);
            row.setMenuId(menuId);
            row.setCreatedAt(now);
            rows.add(row);
        }
        easyEntityQuery.insertable(rows).executeRows();
    }

    public void replaceApis(Long roleId, List<Long> apiIds) {
        clearApis(roleId);
        if (apiIds == null || apiIds.isEmpty()) {
            return;
        }
        LocalDateTime now = TimeZones.now();
        List<SysRoleApi> rows = new ArrayList<>(apiIds.size());
        for (Long apiId : apiIds) {
            SysRoleApi row = new SysRoleApi();
            row.setRoleId(roleId);
            row.setApiId(apiId);
            row.setCreatedAt(now);
            rows.add(row);
        }
        easyEntityQuery.insertable(rows).executeRows();
    }

    public void clearBindings(Long roleId) {
        clearMenus(roleId);
        clearApis(roleId);
    }

    public void clearMenus(Long roleId) {
        easyEntityQuery
                .deletable(SysRoleMenu.class)
                .where(rm -> rm.roleId().eq(roleId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    /** 菜单软删前清除所有角色对该菜单的绑定。 */
    public void clearMenusByMenuId(Long menuId) {
        easyEntityQuery
                .deletable(SysRoleMenu.class)
                .where(rm -> rm.menuId().eq(menuId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    public void clearApis(Long roleId) {
        easyEntityQuery
                .deletable(SysRoleApi.class)
                .where(ra -> ra.roleId().eq(roleId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    /** API 软删前：返回绑定了该 API 的角色 ID 列表。 */
    public List<Long> findRoleIdsByApiId(Long apiId) {
        return easyEntityQuery
                .queryable(SysRoleApi.class)
                .where(ra -> ra.apiId().eq(apiId))
                .select(SysRoleApiProxy::roleId)
                .distinct()
                .toList();
    }

    /** API 软删前清除所有角色对该 API 的绑定。 */
    public void clearApisByApiId(Long apiId) {
        easyEntityQuery
                .deletable(SysRoleApi.class)
                .where(ra -> ra.apiId().eq(apiId))
                .allowDeleteStatement(true)
                .executeRows();
    }

    public Set<Long> boundMenuIdSet(Long roleId) {
        return new HashSet<>(findMenuIdsByRoleId(roleId));
    }

    public Set<Long> boundApiIdSet(Long roleId) {
        return new HashSet<>(findApiIdsByRoleId(roleId));
    }
}
