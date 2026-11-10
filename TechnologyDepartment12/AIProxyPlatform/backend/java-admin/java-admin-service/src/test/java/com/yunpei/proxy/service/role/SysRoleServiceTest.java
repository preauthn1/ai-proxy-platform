package com.yunpei.proxy.service.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.entity.SysRole;
import com.yunpei.proxy.service.repository.SysRoleBindingRepository;
import com.yunpei.proxy.service.repository.SysRoleRepository;
import com.yunpei.proxy.service.repository.SysUserRoleRepository;
import com.yunpei.proxy.service.role.RoleManageModels.CreateRoleCommand;
import com.yunpei.proxy.service.role.RoleManageModels.ParentIdChange;
import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleListQuery;
import com.yunpei.proxy.service.role.RoleManageModels.RoleView;
import com.yunpei.proxy.service.role.RoleManageModels.UpdateRoleCommand;
import com.yunpei.proxy.service.user.SysUserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link SysRoleService}：Root 保护、绑定替换、Casbin 同步触发。
 */
class SysRoleServiceTest {

    private final SysRoleRepository roleRepo = mock(SysRoleRepository.class);
    private final SysRoleBindingRepository bindingRepo = mock(SysRoleBindingRepository.class);
    private final SysUserRoleRepository userRoleRepo = mock(SysUserRoleRepository.class);
    private final SysUserService userService = mock(SysUserService.class);
    private SysRoleService service;

    @BeforeEach
    void init() {
        service = new SysRoleService(
                roleRepo, bindingRepo, userRoleRepo, userService, new io.github.linpeilie.Converter());
    }

    @Test
    void pageRoles_attachesUserCountAndParentName() {
        SysRole child = role(2L, "ops", "运维", 1L);
        EasyPageResult<SysRole> page = new EasyPageResult<>() {
            @Override
            public List<SysRole> getData() {
                return List.of(child);
            }

            @Override
            public long getTotal() {
                return 1L;
            }
        };
        when(roleRepo.page(ArgumentMatchers.any(RoleListQuery.class))).thenReturn(page);
        when(userRoleRepo.countActiveUsersByRoleIds(List.of(2L))).thenReturn(Map.of(2L, 3L));
        when(roleRepo.findNamesByIds(List.of(1L))).thenReturn(Map.of(1L, "超级管理员"));

        PageData<RoleView> result = service.pageRoles(RoleListQuery.of(1, 20, null, null, null));

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).userCount()).isEqualTo(3L);
        assertThat(result.getItems().get(0).parentName()).isEqualTo("超级管理员");
        assertThat(result.getItems().get(0).code()).isEqualTo("ops");
    }

    @Test
    void create_insertsAndReturnsView() {
        when(roleRepo.existsByCode("ops")).thenReturn(false);
        doAnswer(inv -> {
                    SysRole r = inv.getArgument(0);
                    r.setId(10L);
                    r.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    r.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    r.setCreatedBy(0L);
                    r.setUpdatedBy(0L);
                    r.setDeletedAt(0L);
                    return null;
                })
                .when(roleRepo)
                .insert(ArgumentMatchers.any(SysRole.class));
        when(roleRepo.findById(10L)).thenReturn(role(10L, "ops", "运维", null));
        when(userRoleRepo.countActiveUsersByRoleIds(List.of(10L))).thenReturn(Map.of(10L, 0L));

        RoleView view = service.create(new CreateRoleCommand("ops", "运维", null, 5, 1, "备注"));

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.code()).isEqualTo("ops");
        ArgumentCaptor<SysRole> cap = ArgumentCaptor.forClass(SysRole.class);
        verify(roleRepo).insert(cap.capture());
        assertThat(cap.getValue().getSort()).isEqualTo(5);
        assertThat(cap.getValue().getName()).isEqualTo("运维");
    }

    @Test
    void softDelete_root_throws() {
        when(roleRepo.findById(1L)).thenReturn(role(1L, "root", "超级管理员", null));

        assertThatThrownBy(() -> service.softDelete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Root");
        verify(roleRepo, never()).softDeleteById(ArgumentMatchers.any());
        verify(bindingRepo, never()).clearBindings(ArgumentMatchers.any());
    }

    @Test
    void softDelete_withUsers_throws() {
        when(roleRepo.findById(2L)).thenReturn(role(2L, "ops", "运维", null));
        when(userRoleRepo.hasActiveUsers(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户");
        verify(roleRepo, never()).softDeleteById(2L);
    }

    @Test
    void softDelete_clearsBindings() {
        when(roleRepo.findById(2L)).thenReturn(role(2L, "ops", "运维", null));
        when(userRoleRepo.hasActiveUsers(2L)).thenReturn(false);
        when(roleRepo.hasChildren(2L)).thenReturn(false);
        when(roleRepo.softDeleteById(2L)).thenReturn(1L);

        RoleView view = service.softDelete(2L);

        assertThat(view.code()).isEqualTo("ops");
        assertThat(view.deletedAt()).isGreaterThan(0L);
        verify(bindingRepo).clearBindings(2L);
        verify(roleRepo).softDeleteById(2L);
    }

    @Test
    void update_disableRoot_throws() {
        when(roleRepo.findById(1L)).thenReturn(role(1L, "root", "超级管理员", null));

        assertThatThrownBy(
                        () -> service.update(new UpdateRoleCommand(1L, null, ParentIdChange.absent(), null, 0, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可禁用");
    }

    @Test
    void replaceApis_syncsCasbinForBoundUsers() {
        when(roleRepo.findById(2L)).thenReturn(role(2L, "ops", "运维", null));
        when(bindingRepo.filterExistingApiIds(List.of(100L, 101L))).thenReturn(List.of(100L, 101L));
        when(userRoleRepo.findActiveUserIdsByRoleId(2L)).thenReturn(List.of(5L, 6L));

        RoleApiBindResult result = service.replaceApis(2L, List.of(100L, 101L));

        assertThat(result.roleId()).isEqualTo(2L);
        assertThat(result.apiIds()).containsExactly(100L, 101L);
        verify(bindingRepo).replaceApis(2L, List.of(100L, 101L));
        verify(userService).syncCasbinForUser(5L);
        verify(userService).syncCasbinForUser(6L);
    }

    @Test
    void replaceMenus_doesNotSyncCasbin() {
        when(roleRepo.findById(2L)).thenReturn(role(2L, "ops", "运维", null));
        when(bindingRepo.filterExistingMenuIds(List.of(1L))).thenReturn(List.of(1L));

        service.replaceMenus(2L, List.of(1L));

        verify(bindingRepo).replaceMenus(2L, List.of(1L));
        verify(userService, never()).syncCasbinForUser(ArgumentMatchers.any());
    }

    @Test
    void listApiBinds_marksBound() {
        when(roleRepo.findById(2L)).thenReturn(role(2L, "ops", "运维", null));
        when(bindingRepo.boundApiIdSet(2L)).thenReturn(java.util.Set.of(10L));
        SysApi a1 = api(10L, "List", "GET", "/api/a");
        SysApi a2 = api(11L, "Create", "POST", "/api/a");
        when(bindingRepo.listAllApis()).thenReturn(List.of(a1, a2));

        var items = service.listApiBinds(2L);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).bound()).isTrue();
        assertThat(items.get(1).bound()).isFalse();
    }

    private static SysRole role(Long id, String code, String name, Long parentId) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setCode(code);
        r.setName(name);
        r.setParentId(parentId);
        r.setSort(0);
        r.setRemark("");
        r.setIsEnabled(1);
        r.setDeletedAt(0L);
        r.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        r.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        r.setCreatedBy(0L);
        r.setUpdatedBy(0L);
        return r;
    }

    private static SysApi api(Long id, String name, String method, String path) {
        SysApi a = new SysApi();
        a.setId(id);
        a.setName(name);
        a.setMethod(method);
        a.setPath(path);
        a.setPermissionCode("p:" + id);
        a.setApiGroup("g");
        a.setIsEnabled(1);
        return a;
    }
}
