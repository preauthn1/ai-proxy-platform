package com.yunpei.proxy.service.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.menu.MenuManageModels.RuntimeMenuRoute;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 动态菜单投影纯函数测试。
 */
class RuntimeMenuProjectorTest {

    @Test
    void expandMenuIdsWithAncestors_includesParents() {
        SysMenu root = menu(1L, null, "系统", "DIR", "/system", 1);
        SysMenu child = menu(2L, 1L, "用户", "MENU", "/system/user", 1);
        Set<Long> expanded = RuntimeMenuProjector.expandMenuIdsWithAncestors(List.of(2L), List.of(root, child));
        assertThat(expanded).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void buildRuntimeMenuTree_prunesButtonAndEmptyDir() {
        SysMenu root = menu(1L, null, "系统", "DIR", "/system", 1);
        SysMenu page = menu(2L, 1L, "用户", "MENU", "/system/user", 1);
        page.setComponent("/system/user/index");
        SysMenu btn = menu(3L, 2L, "新增", "BUTTON", null, 1);
        btn.setPermissionCode("system:user:create");

        List<RuntimeMenuRoute> tree =
                RuntimeMenuProjector.buildRuntimeMenuTree(List.of(root, page, btn), Set.of(1L, 2L, 3L));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).path()).isEqualTo("/system");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).component()).isEqualTo("/system/user/index");
        assertThat(tree.get(0).redirect()).isEqualTo("/system/user");
    }

    @Test
    void buildRuntimeMenuTree_disabledParent_hidesChildren() {
        SysMenu root = menu(1L, null, "系统", "DIR", "/system", 0);
        SysMenu page = menu(2L, 1L, "用户", "MENU", "/system/user", 1);
        page.setComponent("/system/user/index");

        List<RuntimeMenuRoute> tree = RuntimeMenuProjector.buildRuntimeMenuTree(List.of(root, page), Set.of(1L, 2L));
        assertThat(tree).isEmpty();
    }

    @Test
    void routeNameFromPath_normalizesSegments() {
        assertThat(RuntimeMenuProjector.routeNameFromPath("/system/user", "x")).isEqualTo("SystemUser");
        assertThat(RuntimeMenuProjector.routeNameFromPath(null, "Dashboard")).isEqualTo("Dashboard");
    }

    private static SysMenu menu(Long id, Long parentId, String name, String type, String path, int enabled) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setParentId(parentId);
        m.setName(name);
        m.setType(type);
        m.setPath(path);
        m.setIcon("");
        m.setRedirect("");
        m.setSort(0);
        m.setIsHidden(0);
        m.setIsEnabled(enabled);
        m.setDeletedAt(0L);
        m.setTreePath(parentId == null ? "/" + id + "/" : "/" + parentId + "/" + id + "/");
        return m;
    }
}
