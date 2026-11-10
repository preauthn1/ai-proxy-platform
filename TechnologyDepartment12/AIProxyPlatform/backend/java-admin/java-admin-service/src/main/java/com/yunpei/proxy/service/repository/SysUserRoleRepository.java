package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.SysRole;
import com.yunpei.proxy.service.entity.SysRoleApi;
import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.entity.SysUserRole;
import com.yunpei.proxy.service.entity.proxy.SysRoleApiProxy;
import com.yunpei.proxy.service.entity.proxy.SysRoleProxy;
import com.yunpei.proxy.service.entity.proxy.SysUserProxy;
import com.yunpei.proxy.service.entity.proxy.SysUserRoleProxy;
import com.yunpei.proxy.service.port.CasbinPolicyPort.ApiPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户-角色关联与角色 API 展开查询。
 *
 * <p>关联表无软删：解绑/重绑为硬删行再插入。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysUserRoleRepository {

    private final EasyEntityQuery easyEntityQuery;

    /** 用户绑定的角色 ID 列表。 */
    public List<Long> findRoleIdsByUserId(Long userId) {
        return easyEntityQuery
                .queryable(SysUserRole.class)
                .where(ur -> ur.userId().eq(userId))
                .select(SysUserRoleProxy::roleId)
                .toList();
    }

    /**
     * 批量查询用户 → 角色 ID 映射（用于列表组装）。
     */
    public Map<Long, List<Long>> findRoleIdsByUserIds(List<Long> userIds) {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }
        for (Long id : userIds) {
            map.put(id, new ArrayList<>());
        }
        List<SysUserRole> rows = easyEntityQuery
                .queryable(SysUserRole.class)
                .where(ur -> ur.userId().in(userIds))
                .toList();
        for (SysUserRole row : rows) {
            map.computeIfAbsent(row.getUserId(), k -> new ArrayList<>()).add(row.getRoleId());
        }
        return map;
    }

    /**
     * 校验角色均存在且未软删；返回合法角色 ID 列表（去重、保序）。
     *
     * @return null 表示有非法 ID；空列表表示合法但无角色
     */
    public List<Long> filterExistingRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = roleIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Long> found = easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> r.id().in(distinct))
                .select(SysRoleProxy::id)
                .toList();
        if (found.size() != distinct.size()) {
            return null;
        }
        return distinct;
    }

    /** 角色名映射（未软删）。 */
    public Map<Long, String> findRoleNamesByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        return easyEntityQuery.queryable(SysRole.class).where(r -> r.id().in(roleIds)).toList().stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a, LinkedHashMap::new));
    }

    /** 角色编码是否包含 root。 */
    public boolean userHasRootRole(Long userId) {
        List<Long> roleIds = findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return false;
        }
        return easyEntityQuery
                .queryable(SysRole.class)
                .where(r -> {
                    r.id().in(roleIds);
                    r.code().eq("root");
                })
                .any();
    }

    /**
     * 全量替换用户角色：硬删旧行再插入。
     */
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        easyEntityQuery
                .deletable(SysUserRole.class)
                .where(ur -> ur.userId().eq(userId))
                .allowDeleteStatement(true)
                .executeRows();
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = TimeZones.now();
        List<SysUserRole> rows = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setCreatedAt(now);
            rows.add(ur);
        }
        easyEntityQuery.insertable(rows).executeRows();
    }

    /** 清除用户全部角色绑定。 */
    public void clearUserRoles(Long userId) {
        replaceUserRoles(userId, List.of());
    }

    /** 绑定了该角色的用户 ID（关联表无软删；调用方自行过滤已软删用户）。 */
    public List<Long> findUserIdsByRoleId(Long roleId) {
        return easyEntityQuery
                .queryable(SysUserRole.class)
                .where(ur -> ur.roleId().eq(roleId))
                .select(SysUserRoleProxy::userId)
                .distinct()
                .toList();
    }

    /** 绑定了该角色且未软删的用户 ID（用于 Casbin 同步）。 */
    public List<Long> findActiveUserIdsByRoleId(Long roleId) {
        List<Long> userIds = findUserIdsByRoleId(roleId);
        if (userIds.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> u.id().in(userIds))
                .select(SysUserProxy::id)
                .toList();
    }

    /**
     * 批量统计角色下未软删用户数。
     *
     * @return roleId → count
     */
    public Map<Long, Long> countActiveUsersByRoleIds(List<Long> roleIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return counts;
        }
        for (Long roleId : roleIds) {
            counts.put(roleId, 0L);
        }
        List<SysUserRole> rows = easyEntityQuery
                .queryable(SysUserRole.class)
                .where(ur -> ur.roleId().in(roleIds))
                .toList();
        if (rows.isEmpty()) {
            return counts;
        }
        List<Long> userIds =
                rows.stream().map(SysUserRole::getUserId).distinct().toList();
        // SysUser 带 LogicDelete：仅未软删
        List<Long> activeUserIds = easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> u.id().in(userIds))
                .select(SysUserProxy::id)
                .toList();
        Set<Long> active = new HashSet<>(activeUserIds);
        for (SysUserRole row : rows) {
            if (!active.contains(row.getUserId())) {
                continue;
            }
            counts.merge(row.getRoleId(), 1L, Long::sum);
        }
        return counts;
    }

    /** 该角色是否仍有未软删用户绑定。 */
    public boolean hasActiveUsers(Long roleId) {
        List<Long> userIds = findUserIdsByRoleId(roleId);
        if (userIds.isEmpty()) {
            return false;
        }
        return easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> u.id().in(userIds))
                .any();
    }

    /**
     * 按用户角色展开启用中的 API path+method（用于 Casbin 策略同步）。
     */
    public List<ApiPolicy> findApiPoliciesByUserId(Long userId) {
        List<Long> roleIds = findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> apiIds = easyEntityQuery
                .queryable(SysRoleApi.class)
                .where(ra -> ra.roleId().in(roleIds))
                .select(SysRoleApiProxy::apiId)
                .distinct()
                .toList();
        if (apiIds.isEmpty()) {
            return List.of();
        }
        List<SysApi> apis = easyEntityQuery
                .queryable(SysApi.class)
                .where(a -> {
                    a.id().in(apiIds);
                    a.isEnabled().eq(1);
                })
                .toList();
        List<ApiPolicy> policies = new ArrayList<>();
        for (SysApi api : apis) {
            if (api.getPath() == null
                    || api.getMethod() == null
                    || api.getMethod().isBlank()) {
                continue;
            }
            policies.add(new ApiPolicy(api.getPath(), api.getMethod().toUpperCase(Locale.ROOT)));
        }
        return policies;
    }
}
