package com.yunpei.proxy.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.menu.MenuManageModels.CreateMenuCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListPage;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListQuery;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuView;
import com.yunpei.proxy.service.menu.MenuManageModels.ParentIdChange;
import com.yunpei.proxy.service.menu.MenuManageModels.UpdateMenuCommand;
import com.yunpei.proxy.service.repository.AuthQueryRepository;
import com.yunpei.proxy.service.repository.SysMenuApiRepository;
import com.yunpei.proxy.service.repository.SysMenuRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link SysMenuService} 业务行为：树校验、软删约束、API 绑定。
 */
class SysMenuServiceTest {

    private final SysMenuRepository menuRepo = mock(SysMenuRepository.class);
    private final SysMenuApiRepository menuApiRepo = mock(SysMenuApiRepository.class);
    private final AuthQueryRepository authQueryRepo = mock(AuthQueryRepository.class);
    private SysMenuService service;

    @BeforeEach
    void init() {
        service = new SysMenuService(menuRepo, menuApiRepo, authQueryRepo, new io.github.linpeilie.Converter());
    }

    @Test
    void pageMenus_pagesByRootsAndExpandsSubtree() {
        SysMenu root1 = menu(1L, null, "A", "DIR");
        SysMenu child = menu(2L, 1L, "A1", "MENU");
        SysMenu root2 = menu(3L, null, "B", "DIR");
        when(menuRepo.listAll()).thenReturn(List.of(root1, child, root2));

        MenuListPage page = service.pageMenus(MenuListQuery.of(1, 1, null, null, null, null));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.itemTotal()).isEqualTo(3);
        assertThat(page.items()).extracting(MenuView::id).containsExactly(1L, 2L);
    }

    @Test
    void create_buttonWithoutPermission_throws() {
        assertThatThrownBy(() -> service.create(new CreateMenuCommand(
                        null, "btn", "BUTTON", null, null, null, null, null, null, 0, 0, 1, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("permissionCode");
        verify(menuRepo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_menu_setsTreePathAfterInsert() {
        doAnswer(inv -> {
                    SysMenu m = inv.getArgument(0);
                    m.setId(10L);
                    return null;
                })
                .when(menuRepo)
                .insert(ArgumentMatchers.any(SysMenu.class));
        when(menuRepo.findById(10L)).thenAnswer(inv -> {
            SysMenu m = menu(10L, null, "仪表盘", "MENU");
            m.setPath("/dashboard");
            m.setTreePath("/10/");
            return m;
        });

        MenuView view = service.create(new CreateMenuCommand(
                null, "仪表盘", "MENU", "/dashboard", "/dashboard/index", "icon", "", null, null, 0, 0, 1, ""));

        assertThat(view.id()).isEqualTo(10L);
        verify(menuRepo).updateTreePath(10L, "/10/");
    }

    @Test
    void softDelete_withChildren_throws() {
        when(menuRepo.findById(1L)).thenReturn(menu(1L, null, "A", "DIR"));
        when(menuRepo.hasChildren(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("子菜单");
        verify(menuRepo, never()).softDeleteById(ArgumentMatchers.any());
    }

    @Test
    void softDelete_clearsBindings() {
        when(menuRepo.findById(2L)).thenReturn(menu(2L, null, "A", "MENU"));
        when(menuRepo.hasChildren(2L)).thenReturn(false);
        when(menuRepo.softDeleteById(2L)).thenReturn(1L);

        MenuView view = service.softDelete(2L);

        assertThat(view.deletedAt()).isGreaterThan(0L);
        verify(menuApiRepo).clearByMenuId(2L);
        verify(menuApiRepo).clearRoleMenusByMenuId(2L);
    }

    @Test
    void setMenuApis_replacesBindings() {
        when(menuRepo.findById(5L)).thenReturn(menu(5L, null, "A", "MENU"));
        when(menuApiRepo.retainExistingApiIds(List.of(1L, 2L))).thenReturn(List.of(1L, 2L));
        when(menuApiRepo.replaceApis(5L, List.of(1L, 2L))).thenReturn(List.of(1L, 2L));

        MenuApiBindResult result = service.setMenuApis(5L, List.of(1L, 2L));

        assertThat(result.menuId()).isEqualTo(5L);
        assertThat(result.apiIds()).containsExactly(1L, 2L);
    }

    @Test
    void batch_delete_rollsBackOnChild() {
        when(menuRepo.listByIds(List.of(1L, 2L)))
                .thenReturn(List.of(menu(1L, null, "A", "DIR"), menu(2L, null, "B", "DIR")));
        when(menuRepo.hasChildren(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.batch(new MenuBatchCommand("delete", List.of(1L, 2L))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("子菜单");
        verify(menuRepo, never()).softDeleteById(ArgumentMatchers.any());
    }

    @Test
    void update_cannotMoveUnderDescendant() {
        SysMenu self = menu(1L, null, "A", "DIR");
        self.setTreePath("/1/");
        SysMenu child = menu(2L, 1L, "B", "DIR");
        child.setTreePath("/1/2/");
        when(menuRepo.findById(1L)).thenReturn(self);
        when(menuRepo.findById(2L)).thenReturn(child);

        assertThatThrownBy(() -> service.update(new UpdateMenuCommand(
                        1L,
                        ParentIdChange.of(2L),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MenuManageModels.MetadataChange.absent(),
                        null,
                        null,
                        null,
                        null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("后代");
    }

    @Test
    void listRuntimeMenusForUser_usesGrantedAndProjector() {
        SysMenu root = menu(1L, null, "系统", "DIR");
        root.setPath("/system");
        SysMenu page = menu(2L, 1L, "用户", "MENU");
        page.setPath("/system/user");
        page.setComponent("/system/user/index");
        when(menuRepo.listAll()).thenReturn(List.of(root, page));
        when(authQueryRepo.findGrantedMenuIdsByUserId(9L)).thenReturn(List.of(2L));

        var routes = service.listRuntimeMenusForUser(9L);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).children()).hasSize(1);
    }

    @Test
    void nameExists_delegates() {
        when(menuRepo.existsByName("x", 3L)).thenReturn(true);
        assertThat(service.nameExists("x", 3L)).isTrue();
    }

    private static SysMenu menu(Long id, Long parentId, String name, String type) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setParentId(parentId);
        m.setName(name);
        m.setType(type);
        m.setIcon("");
        m.setRedirect("");
        m.setSort(0);
        m.setIsHidden(0);
        m.setIsEnabled(1);
        m.setDeletedAt(0L);
        m.setTreePath(parentId == null ? "/" + id + "/" : "/" + parentId + "/" + id + "/");
        m.setRemark("");
        return m;
    }
}
