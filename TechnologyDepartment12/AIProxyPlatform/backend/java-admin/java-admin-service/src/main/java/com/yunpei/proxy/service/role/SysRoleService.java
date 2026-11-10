package com.yunpei.proxy.service.role;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.entity.SysRole;
import com.yunpei.proxy.service.repository.SysRoleBindingRepository;
import com.yunpei.proxy.service.repository.SysRoleRepository;
import com.yunpei.proxy.service.repository.SysUserRoleRepository;
import com.yunpei.proxy.service.role.RoleManageModels.CreateRoleCommand;
import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindView;
import com.yunpei.proxy.service.role.RoleManageModels.RoleListQuery;
import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindView;
import com.yunpei.proxy.service.role.RoleManageModels.RoleView;
import com.yunpei.proxy.service.role.RoleManageModels.UpdateRoleCommand;
import com.yunpei.proxy.service.user.SysUserService;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统角色 Service：分页/CRUD/软删、菜单与 API 绑定、角色 API 变更后 Casbin 同步。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleRepository roleRepository;
    private final SysRoleBindingRepository bindingRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysUserService sysUserService;
    private final Converter converter;

    public PageData<RoleView> pageRoles(RoleListQuery query) {
        EasyPageResult<SysRole> page = roleRepository.page(query);
        List<SysRole> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(toViews(rows), page.getTotal());
    }

    public List<RoleView> listAll(Integer status) {
        return toViews(roleRepository.listAll(status));
    }

    public RoleView create(CreateRoleCommand cmd) {
        String code = requireNonBlank(cmd.code(), "code");
        String name = requireNonBlank(cmd.name(), "name");
        if (roleRepository.existsByCode(code)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "角色编码 " + code + " 已存在");
        }
        Long parentId = normalizeParentId(cmd.parentId());
        if (parentId != null && !roleRepository.existsById(parentId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "父角色 " + parentId + " 不存在");
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setParentId(parentId);
        role.setSort(cmd.sort() == null ? 0 : cmd.sort());
        role.setRemark(nullToEmpty(cmd.remark()));
        role.setIsEnabled(cmd.isEnabled() == null ? 1 : (cmd.isEnabled() == 0 ? 0 : 1));

        roleRepository.insert(role);
        return loadView(role.getId());
    }

    public RoleView update(UpdateRoleCommand cmd) {
        SysRole role = requireRole(cmd.id());
        if (cmd.name() != null) {
            String name = cmd.name().trim();
            if (name.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name 不能为空");
            }
            role.setName(name);
        }
        if (cmd.parentId() != null && cmd.parentId().present()) {
            Long parentId = normalizeParentId(cmd.parentId().value());
            validateParentChange(role.getId(), parentId);
            role.setParentId(parentId);
        }
        if (cmd.sort() != null) {
            role.setSort(cmd.sort());
        }
        if (cmd.isEnabled() != null) {
            int enabled = cmd.isEnabled() == 0 ? 0 : 1;
            if (enabled == 0 && isRootRole(role)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "内置 Root 角色不可禁用");
            }
            role.setIsEnabled(enabled);
        }
        if (cmd.remark() != null) {
            role.setRemark(cmd.remark().trim());
        }
        roleRepository.update(role);
        return loadView(role.getId());
    }

    /**
     * 软删角色：Root 不可删；有用户/子角色拒绝；清菜单/API 绑定后软删。
     */
    public RoleView softDelete(Long id) {
        SysRole role = requireRole(id);
        if (isRootRole(role)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "内置 Root 角色不可删除");
        }
        if (userRoleRepository.hasActiveUsers(id)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "该角色下存在用户，请先移除用户角色绑定");
        }
        if (roleRepository.hasChildren(id)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "请先删除子角色");
        }

        bindingRepository.clearBindings(id);
        long rows = roleRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "角色 " + id + " 不存在");
        }
        long deletedAt = System.currentTimeMillis();
        role.setDeletedAt(deletedAt);
        return toView(role, 0L, null);
    }

    public List<RoleMenuBindView> listMenuBinds(Long roleId) {
        requireRole(roleId);
        Set<Long> bound = bindingRepository.boundMenuIdSet(roleId);
        List<SysMenu> menus = bindingRepository.listAllMenus();
        List<RoleMenuBindView> items = new ArrayList<>(menus.size());
        for (SysMenu m : menus) {
            items.add(new RoleMenuBindView(
                    m.getId(),
                    m.getParentId(),
                    m.getName(),
                    m.getType(),
                    m.getPath(),
                    m.getComponent(),
                    m.getIcon(),
                    m.getRedirect(),
                    m.getPermissionCode(),
                    m.getTreePath(),
                    m.getMetadata(),
                    m.getSort(),
                    m.getIsHidden(),
                    m.getIsEnabled(),
                    nullToEmpty(m.getRemark()),
                    m.getDeletedAt() == null ? 0L : m.getDeletedAt(),
                    m.getCreatedAt(),
                    m.getUpdatedAt(),
                    bound.contains(m.getId())));
        }
        return items;
    }

    public RoleMenuBindResult replaceMenus(Long roleId, List<Long> menuIds) {
        requireRole(roleId);
        if (menuIds == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "menuIds 必须为数组");
        }
        List<Long> filtered = bindingRepository.filterExistingMenuIds(menuIds);
        if (filtered == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "存在无效菜单 ID");
        }
        bindingRepository.replaceMenus(roleId, filtered);
        // 菜单绑定不影响 Casbin p 策略（仅 API 绑定展开）
        return new RoleMenuBindResult(roleId, filtered);
    }

    public List<RoleApiBindView> listApiBinds(Long roleId) {
        requireRole(roleId);
        Set<Long> bound = bindingRepository.boundApiIdSet(roleId);
        List<SysApi> apis = bindingRepository.listAllApis();
        List<RoleApiBindView> items = new ArrayList<>(apis.size());
        for (SysApi a : apis) {
            items.add(new RoleApiBindView(
                    a.getId(),
                    a.getName(),
                    a.getMethod(),
                    a.getPath(),
                    a.getPermissionCode(),
                    a.getApiGroup(),
                    a.getIsEnabled() == null ? 0 : a.getIsEnabled(),
                    bound.contains(a.getId())));
        }
        return items;
    }

    /**
     * 全量替换角色 API 绑定，并对绑定了该角色的用户重算 Casbin p 策略。
     */
    public RoleApiBindResult replaceApis(Long roleId, List<Long> apiIds) {
        requireRole(roleId);
        if (apiIds == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "apiIds 必须为数组");
        }
        List<Long> filtered = bindingRepository.filterExistingApiIds(apiIds);
        if (filtered == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "存在无效接口 ID");
        }
        bindingRepository.replaceApis(roleId, filtered);
        syncCasbinForRoleUsers(roleId);
        return new RoleApiBindResult(roleId, filtered);
    }

    private void syncCasbinForRoleUsers(Long roleId) {
        List<Long> userIds = userRoleRepository.findActiveUserIdsByRoleId(roleId);
        for (Long userId : userIds) {
            sysUserService.syncCasbinForUser(userId);
        }
        log.atInfo()
                .addKeyValue("roleId", roleId)
                .addKeyValue("userCount", userIds.size())
                .addKeyValue("logType", "ROLE")
                .log("casbin synced");
    }

    private List<RoleView> toViews(List<SysRole> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = rows.stream().map(SysRole::getId).toList();
        Map<Long, Long> userCounts = userRoleRepository.countActiveUsersByRoleIds(roleIds);
        List<Long> parentIds = rows.stream()
                .map(SysRole::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> parentNames = roleRepository.findNamesByIds(parentIds);

        List<RoleView> items = new ArrayList<>(rows.size());
        for (SysRole row : rows) {
            Long pid = row.getParentId();
            items.add(toView(row, userCounts.getOrDefault(row.getId(), 0L), pid == null ? null : parentNames.get(pid)));
        }
        return items;
    }

    private RoleView loadView(Long id) {
        SysRole role = requireRole(id);
        Map<Long, Long> counts = userRoleRepository.countActiveUsersByRoleIds(List.of(id));
        String parentName = null;
        if (role.getParentId() != null) {
            parentName =
                    roleRepository.findNamesByIds(List.of(role.getParentId())).get(role.getParentId());
        }
        return toView(role, counts.getOrDefault(id, 0L), parentName);
    }

    private RoleView toView(SysRole role, Long userCount, String parentName) {
        RoleView base = converter.convert(role, RoleView.class);
        return new RoleView(
                base.id(),
                base.code(),
                base.name(),
                base.parentId(),
                base.sort(),
                base.remark(),
                base.isEnabled(),
                base.deletedAt(),
                base.createdAt(),
                base.updatedAt(),
                base.createdBy(),
                base.updatedBy(),
                userCount == null ? 0L : userCount,
                parentName);
    }

    private SysRole requireRole(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        SysRole role = roleRepository.findById(id);
        if (role == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "角色 " + id + " 不存在");
        }
        return role;
    }

    private void validateParentChange(Long roleId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(roleId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "parentId 不能是自己");
        }
        if (!roleRepository.existsById(parentId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "父角色 " + parentId + " 不存在");
        }
        // 沿父链向上：不能把角色挂到自身后代下（成环）
        Set<Long> visited = new HashSet<>();
        Long cur = parentId;
        // 批量预取祖先，减少 findById 往返
        while (cur != null && visited.add(cur)) {
            if (cur.equals(roleId)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "不能将角色移到自身后代下（成环）");
            }
            SysRole parent = roleRepository.findById(cur);
            cur = parent == null ? null : parent.getParentId();
        }
    }

    private static boolean isRootRole(SysRole role) {
        return RoleManageModels.ROOT_ROLE_CODE.equalsIgnoreCase(role.getCode());
    }

    private static Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId <= 0) {
            return null;
        }
        return parentId;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " 不能为空");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
