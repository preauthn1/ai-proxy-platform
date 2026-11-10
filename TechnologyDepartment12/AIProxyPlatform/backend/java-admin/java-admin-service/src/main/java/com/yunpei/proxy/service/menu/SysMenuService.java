package com.yunpei.proxy.service.menu;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.menu.MenuManageModels.ApisByMenusResult;
import com.yunpei.proxy.service.menu.MenuManageModels.CreateMenuCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindView;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListPage;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListQuery;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuView;
import com.yunpei.proxy.service.menu.MenuManageModels.RuntimeMenuRoute;
import com.yunpei.proxy.service.menu.MenuManageModels.UpdateMenuCommand;
import com.yunpei.proxy.service.repository.AuthQueryRepository;
import com.yunpei.proxy.service.repository.SysMenuApiRepository;
import com.yunpei.proxy.service.repository.SysMenuRepository;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统菜单 Service：树形 CRUD/软删/batch、menu-api 绑定、动态路由投影。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuRepository menuRepository;
    private final SysMenuApiRepository menuApiRepository;
    private final AuthQueryRepository authQueryRepository;
    private final Converter converter;

    /**
     * 分页列出菜单：分页基数为根节点；items 为展开子树扁平列表。
     */
    public MenuListPage pageMenus(MenuListQuery query) {
        List<SysMenu> all = menuRepository.listAll();
        Map<Long, SysMenu> byId = indexById(all);
        Map<Long, List<SysMenu>> childrenMap = buildChildrenMap(all);

        boolean hasFilter = query.name() != null
                || query.type() != null
                || query.permissionCode() != null
                || query.status() != null;

        List<SysMenu> roots;
        if (!hasFilter) {
            roots = new ArrayList<>(childrenMap.getOrDefault(null, List.of()));
        } else {
            Set<Long> rootIds = new HashSet<>();
            for (SysMenu m : all) {
                if (matches(m, query)) {
                    rootIds.add(findRoot(m, byId).getId());
                }
            }
            roots = all.stream()
                    .filter(m -> m.getParentId() == null && rootIds.contains(m.getId()))
                    .sorted(menuOrder())
                    .toList();
        }

        long itemTotal = 0;
        for (SysMenu r : roots) {
            itemTotal += collectSubtree(r, childrenMap).size();
        }

        int from = Math.max(0, (query.page() - 1) * query.pageSize());
        int to = Math.min(roots.size(), from + query.pageSize());
        List<SysMenu> pageRoots = from >= roots.size() ? List.of() : roots.subList(from, to);

        List<MenuView> items = new ArrayList<>();
        for (SysMenu r : pageRoots) {
            for (SysMenu node : collectSubtree(r, childrenMap)) {
                items.add(converter.convert(node, MenuView.class));
            }
        }
        return new MenuListPage(items, roots.size(), itemTotal);
    }

    /** 全量未软删菜单（父菜单下拉等）。 */
    public List<MenuView> listAll(String type, Integer status) {
        return converter.convert(menuRepository.listAll(type, status), MenuView.class);
    }

    public MenuView create(CreateMenuCommand cmd) {
        String name = requireNonBlank(cmd.name(), "name");
        String type = requireAllowedType(cmd.type());
        String permissionCode = normalizePermissionCode(cmd.permissionCode());
        if (MenuManageModels.TYPE_BUTTON.equals(type) && (permissionCode == null || permissionCode.isBlank())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "BUTTON 类型必须填写 permissionCode");
        }

        Long parentId = normalizeParentId(cmd.parentId());
        SysMenu parent = null;
        if (parentId != null) {
            parent = requireMenu(parentId);
            if (MenuManageModels.TYPE_BUTTON.equals(parent.getType())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "BUTTON 类型不能作为父菜单");
            }
        }

        String path = null;
        String component = null;
        if (MenuManageModels.TYPE_MENU.equals(type)) {
            path = requireNonBlank(cmd.path(), "path");
            component = blankToNull(cmd.component());
        }
        String icon = MenuManageModels.TYPE_BUTTON.equals(type)
                ? ""
                : nullToEmpty(cmd.icon()).trim();

        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setRedirect(nullToEmpty(cmd.redirect()).trim());
        menu.setPermissionCode(permissionCode);
        menu.setMetadata(blankToNull(cmd.metadata()));
        menu.setSort(cmd.sort() == null ? 0 : cmd.sort());
        menu.setIsHidden(cmd.isHidden() == null ? 0 : (cmd.isHidden() == 0 ? 0 : 1));
        menu.setIsEnabled(cmd.isEnabled() == null ? 1 : (cmd.isEnabled() == 0 ? 0 : 1));
        menu.setRemark(nullToEmpty(cmd.remark()).trim());
        // 占位，insert 回填 id 后再写 tree_path
        menu.setTreePath("/");

        menuRepository.insert(menu);
        String treePath = buildTreePath(menu.getId(), parent);
        menu.setTreePath(treePath);
        menuRepository.updateTreePath(menu.getId(), treePath);
        return loadView(menu.getId());
    }

    public MenuView update(UpdateMenuCommand cmd) {
        SysMenu menu = requireMenu(cmd.id());
        boolean parentChanged = false;

        if (cmd.parentId() != null && cmd.parentId().present()) {
            Long parentId = normalizeParentId(cmd.parentId().value());
            if (Objects.equals(parentId, menu.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "parentId 不能是自己");
            }
            if (parentId != null) {
                SysMenu parent = requireMenu(parentId);
                if (MenuManageModels.TYPE_BUTTON.equals(parent.getType())) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "BUTTON 类型不能作为父菜单");
                }
                // 防止移到自身后代
                String selfPath = menu.getTreePath() == null ? "" : menu.getTreePath();
                String parentPath = parent.getTreePath() == null ? "" : parent.getTreePath();
                if (!selfPath.isEmpty() && parentPath.startsWith(selfPath)) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "不能将菜单移到自身后代下");
                }
            }
            if (!Objects.equals(parentId, menu.getParentId())) {
                menu.setParentId(parentId);
                parentChanged = true;
            }
        }

        if (cmd.name() != null) {
            String name = cmd.name().trim();
            if (name.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name cannot be empty");
            }
            menu.setName(name);
        }

        if (cmd.type() != null) {
            menu.setType(requireAllowedType(cmd.type()));
        }

        String effectiveType = menu.getType();
        if (cmd.path() != null || cmd.type() != null) {
            if (MenuManageModels.TYPE_MENU.equals(effectiveType)) {
                if (cmd.path() != null) {
                    String p = cmd.path().trim();
                    menu.setPath(p.isEmpty() ? null : p);
                }
            } else {
                menu.setPath(null);
            }
        }
        if (cmd.component() != null || cmd.type() != null) {
            if (MenuManageModels.TYPE_MENU.equals(effectiveType)) {
                if (cmd.component() != null) {
                    menu.setComponent(blankToNull(cmd.component()));
                }
            } else {
                menu.setComponent(null);
            }
        }
        if (cmd.icon() != null || cmd.type() != null) {
            if (MenuManageModels.TYPE_BUTTON.equals(effectiveType)) {
                menu.setIcon("");
            } else if (cmd.icon() != null) {
                menu.setIcon(cmd.icon().trim());
            }
        }
        if (cmd.redirect() != null) {
            menu.setRedirect(cmd.redirect().trim());
        }
        if (cmd.permissionCode() != null) {
            String pc = normalizePermissionCode(cmd.permissionCode());
            if (MenuManageModels.TYPE_BUTTON.equals(effectiveType) && (pc == null || pc.isBlank())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "BUTTON 类型必须填写 permissionCode");
            }
            menu.setPermissionCode(pc);
        }
        if (cmd.metadata() != null && cmd.metadata().present()) {
            menu.setMetadata(blankToNull(cmd.metadata().value()));
        }
        if (cmd.sort() != null) {
            menu.setSort(cmd.sort());
        }
        if (cmd.isHidden() != null) {
            menu.setIsHidden(cmd.isHidden() == 0 ? 0 : 1);
        }
        if (cmd.isEnabled() != null) {
            menu.setIsEnabled(cmd.isEnabled() == 0 ? 0 : 1);
        }
        if (cmd.remark() != null) {
            menu.setRemark(cmd.remark().trim());
        }

        if (parentChanged) {
            SysMenu parent = menu.getParentId() == null ? null : menuRepository.findById(menu.getParentId());
            String newPath = buildTreePath(menu.getId(), parent);
            menu.setTreePath(newPath);
        }

        menuRepository.update(menu);
        if (parentChanged) {
            rebuildDescendantTreePaths(menu.getId());
        }
        return loadView(menu.getId());
    }

    /**
     * 软删菜单：有子节点拒绝；清 menu_api / role_menu 后软删。
     */
    public MenuView softDelete(Long id) {
        SysMenu menu = requireMenu(id);
        if (menuRepository.hasChildren(id)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "请先删除子菜单");
        }
        MenuView snapshot = converter.convert(menu, MenuView.class);
        menuApiRepository.clearByMenuId(id);
        menuApiRepository.clearRoleMenusByMenuId(id);
        long rows = menuRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "菜单 " + id + " 不存在");
        }
        return new MenuView(
                snapshot.id(),
                snapshot.parentId(),
                snapshot.name(),
                snapshot.type(),
                snapshot.path(),
                snapshot.component(),
                snapshot.icon(),
                snapshot.redirect(),
                snapshot.permissionCode(),
                snapshot.treePath(),
                snapshot.metadata(),
                snapshot.sort(),
                snapshot.isHidden(),
                snapshot.isEnabled(),
                System.currentTimeMillis(),
                snapshot.remark(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy());
    }

    public MenuBatchResult batch(MenuBatchCommand cmd) {
        String action = cmd.action() == null ? "" : cmd.action().trim();
        if (!Set.of("enable", "disable", "delete").contains(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be enable|disable|delete");
        }
        List<Long> ids = normalizeIds(cmd.ids());
        if (ids.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids must be a non-empty number[]");
        }
        List<SysMenu> targets = menuRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active menu found for given ids");
        }

        if ("delete".equals(action)) {
            for (SysMenu t : targets) {
                if (menuRepository.hasChildren(t.getId())) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "菜单 " + t.getName() + " 存在子菜单，无法删除");
                }
            }
            List<Long> deleted = new ArrayList<>();
            for (SysMenu t : targets) {
                menuApiRepository.clearByMenuId(t.getId());
                menuApiRepository.clearRoleMenusByMenuId(t.getId());
                menuRepository.softDeleteById(t.getId());
                deleted.add(t.getId());
            }
            return new MenuBatchResult(action, deleted.size(), deleted);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (SysMenu t : targets) {
            menuRepository.updateIsEnabled(t.getId(), enabled);
            affected.add(t.getId());
        }
        return new MenuBatchResult(action, affected.size(), affected);
    }

    public List<MenuApiBindView> listMenuApis(Long menuId) {
        requireMenu(menuId);
        Set<Long> bound = menuApiRepository.boundApiIdSet(menuId);
        List<SysApi> apis = menuApiRepository.listAllApis();
        List<MenuApiBindView> out = new ArrayList<>(apis.size());
        for (SysApi a : apis) {
            out.add(new MenuApiBindView(
                    a.getId(),
                    a.getName(),
                    a.getMethod(),
                    a.getPath(),
                    a.getPermissionCode(),
                    a.getApiGroup(),
                    a.getIsEnabled() == null ? 0 : a.getIsEnabled(),
                    bound.contains(a.getId())));
        }
        return out;
    }

    public MenuApiBindResult setMenuApis(Long menuId, List<Long> apiIds) {
        requireMenu(menuId);
        List<Long> filtered = menuApiRepository.retainExistingApiIds(apiIds);
        List<Long> bound = menuApiRepository.replaceApis(menuId, filtered);
        return new MenuApiBindResult(menuId, bound);
    }

    public ApisByMenusResult apisByMenus(List<Long> menuIds) {
        List<Long> ids = normalizeIds(menuIds);
        List<Long> apiIds = menuApiRepository.findDistinctApiIdsByMenuIds(ids);
        return new ApisByMenusResult(ids, apiIds);
    }

    /** name 是否被其他菜单占用（true=已存在冲突）。 */
    public boolean nameExists(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return menuRepository.existsByName(name.trim(), excludeId);
    }

    /** path 是否被其他菜单占用（true=已存在冲突）。 */
    public boolean pathExists(String path, Long excludeId) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return menuRepository.existsByPath(path.trim(), excludeId);
    }

    /**
     * 当前用户动态菜单路由：user → roles → role_menu → 祖先补全 → 投影树。
     */
    public List<RuntimeMenuRoute> listRuntimeMenusForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<SysMenu> menus = menuRepository.listAll();
        Set<Long> granted = findGrantedMenuIds(userId);
        Set<Long> allowed = RuntimeMenuProjector.expandMenuIdsWithAncestors(granted, menus);
        return RuntimeMenuProjector.buildRuntimeMenuTree(menus, allowed);
    }

    private Set<Long> findGrantedMenuIds(Long userId) {
        return new HashSet<>(authQueryRepository.findGrantedMenuIdsByUserId(userId));
    }

    private void rebuildDescendantTreePaths(Long parentId) {
        List<SysMenu> all = menuRepository.listAll();
        Map<Long, List<SysMenu>> childrenMap = buildChildrenMap(all);
        Map<Long, SysMenu> byId = indexById(all);
        rebuildRecursive(parentId, childrenMap, byId);
    }

    private void rebuildRecursive(Long parentId, Map<Long, List<SysMenu>> childrenMap, Map<Long, SysMenu> byId) {
        SysMenu parent = byId.get(parentId);
        if (parent == null) {
            return;
        }
        for (SysMenu child : childrenMap.getOrDefault(parentId, List.of())) {
            String newPath = buildTreePath(child.getId(), parent);
            child.setTreePath(newPath);
            menuRepository.updateTreePath(child.getId(), newPath);
            rebuildRecursive(child.getId(), childrenMap, byId);
        }
    }

    private static String buildTreePath(Long id, SysMenu parent) {
        if (parent == null) {
            return "/" + id + "/";
        }
        String parentPath = parent.getTreePath();
        if (parentPath == null || parentPath.isBlank()) {
            return "/" + parent.getId() + "/" + id + "/";
        }
        return parentPath + id + "/";
    }

    private MenuView loadView(Long id) {
        return converter.convert(requireMenu(id), MenuView.class);
    }

    private SysMenu requireMenu(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        SysMenu menu = menuRepository.findById(id);
        if (menu == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "菜单 " + id + " 不存在");
        }
        return menu;
    }

    private static boolean matches(SysMenu m, MenuListQuery query) {
        if (query.name() != null && (m.getName() == null || !m.getName().contains(query.name()))) {
            return false;
        }
        if (query.type() != null && !query.type().equals(m.getType())) {
            return false;
        }
        if (query.permissionCode() != null) {
            String code = m.getPermissionCode();
            if (code == null
                    || !code.toLowerCase(Locale.ROOT)
                            .contains(query.permissionCode().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (query.status() != null) {
            int enabled = m.getIsEnabled() == null ? 0 : m.getIsEnabled();
            if (enabled != query.status()) {
                return false;
            }
        }
        return true;
    }

    private static SysMenu findRoot(SysMenu m, Map<Long, SysMenu> byId) {
        SysMenu cur = m;
        Set<Long> seen = new HashSet<>();
        while (cur.getParentId() != null) {
            if (!seen.add(cur.getId())) {
                break;
            }
            SysMenu parent = byId.get(cur.getParentId());
            if (parent == null) {
                break;
            }
            cur = parent;
        }
        return cur;
    }

    private static List<SysMenu> collectSubtree(SysMenu root, Map<Long, List<SysMenu>> childrenMap) {
        List<SysMenu> out = new ArrayList<>();
        walk(root, childrenMap, out);
        return out;
    }

    private static void walk(SysMenu node, Map<Long, List<SysMenu>> childrenMap, List<SysMenu> out) {
        out.add(node);
        for (SysMenu c : childrenMap.getOrDefault(node.getId(), List.of())) {
            walk(c, childrenMap, out);
        }
    }

    private static Map<Long, List<SysMenu>> buildChildrenMap(List<SysMenu> all) {
        Map<Long, List<SysMenu>> map = new HashMap<>();
        for (SysMenu m : all) {
            map.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(m);
        }
        for (List<SysMenu> kids : map.values()) {
            kids.sort(menuOrder());
        }
        return map;
    }

    private static Map<Long, SysMenu> indexById(List<SysMenu> all) {
        Map<Long, SysMenu> byId = new LinkedHashMap<>();
        for (SysMenu m : all) {
            byId.put(m.getId(), m);
        }
        return byId;
    }

    private static Comparator<SysMenu> menuOrder() {
        return Comparator.comparingInt((SysMenu m) -> m.getSort() == null ? 0 : m.getSort())
                .thenComparing(SysMenu::getId);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private static Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId <= 0) {
            return null;
        }
        return parentId;
    }

    private static String requireAllowedType(String type) {
        String t = requireNonBlank(type, "type");
        if (!Set.of(MenuManageModels.TYPE_DIR, MenuManageModels.TYPE_MENU, MenuManageModels.TYPE_BUTTON)
                .contains(t)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "type must be DIR | MENU | BUTTON");
        }
        return t;
    }

    private static String normalizePermissionCode(String permissionCode) {
        if (permissionCode == null) {
            return null;
        }
        String trimmed = permissionCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
