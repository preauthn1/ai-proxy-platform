package com.yunpei.proxy.service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchCommand;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListPage;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListQuery;
import com.yunpei.proxy.service.api.ApiManageModels.ApiSyncResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiView;
import com.yunpei.proxy.service.api.ApiManageModels.CreateApiCommand;
import com.yunpei.proxy.service.api.ApiManageModels.UpdateApiCommand;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.repository.SysApiRepository;
import com.yunpei.proxy.service.repository.SysMenuApiRepository;
import com.yunpei.proxy.service.repository.SysRoleBindingRepository;
import com.yunpei.proxy.service.repository.SysUserRoleRepository;
import com.yunpei.proxy.service.user.SysUserService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link SysApiService} 业务行为：按组分页、唯一性、软删清绑定、sync 幂等。
 */
class SysApiServiceTest {

    private final SysApiRepository apiRepo = mock(SysApiRepository.class);
    private final SysMenuApiRepository menuApiRepo = mock(SysMenuApiRepository.class);
    private final SysRoleBindingRepository roleBindingRepo = mock(SysRoleBindingRepository.class);
    private final SysUserRoleRepository userRoleRepo = mock(SysUserRoleRepository.class);
    private final SysUserService userService = mock(SysUserService.class);
    private SysApiService service;

    @BeforeEach
    void init() {
        service = new SysApiService(apiRepo, menuApiRepo, roleBindingRepo, userRoleRepo, userService, new Converter());
    }

    @Test
    void pageApis_pagesByGroupAndFlattensItems() {
        SysApi a1 = api(1L, "A", "GET", "/a", "g:a", "组B");
        SysApi a2 = api(2L, "B", "POST", "/b", "g:b", "组A");
        SysApi a3 = api(3L, "C", "GET", "/c", "g:c", "组A");
        when(apiRepo.listFiltered(null, null, null, null, null)).thenReturn(List.of(a1, a2, a3));

        ApiListPage page = service.pageApis(ApiListQuery.of(1, 1, null, null, null, null, null));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.itemTotal()).isEqualTo(3);
        // Collator zh-CN：组A 在 组B 前；pageSize=1 只取第一组
        assertThat(page.items()).extracting(ApiView::apiGroup).containsOnly("组A");
        assertThat(page.items()).extracting(ApiView::id).containsExactly(2L, 3L);
    }

    @Test
    void create_duplicateMethodPath_throws() {
        when(apiRepo.existsByMethodAndPath("GET", "/x", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateApiCommand("n", "GET", "/x", "p:x", "", "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
        verify(apiRepo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_duplicatePermissionCode_throws() {
        when(apiRepo.existsByMethodAndPath("GET", "/x", null)).thenReturn(false);
        when(apiRepo.existsByPermissionCode("p:x", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateApiCommand("n", "GET", "/x", "p:x", "", "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("permissionCode");
    }

    @Test
    void create_success_insertsAndReturnsView() {
        when(apiRepo.existsByMethodAndPath("GET", "/ok", null)).thenReturn(false);
        when(apiRepo.existsByPermissionCode("ok:get", null)).thenReturn(false);
        doAnswer(inv -> {
                    SysApi a = inv.getArgument(0);
                    a.setId(99L);
                    a.setDeletedAt(0L);
                    a.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    a.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    a.setCreatedBy(0L);
                    a.setUpdatedBy(0L);
                    return null;
                })
                .when(apiRepo)
                .insert(ArgumentMatchers.any(SysApi.class));
        when(apiRepo.findById(99L)).thenAnswer(inv -> {
            SysApi a = api(99L, "ok", "GET", "/ok", "ok:get", "g");
            return a;
        });

        ApiView view = service.create(new CreateApiCommand("ok", "get", "/ok", "ok:get", "g", "r", 1));

        assertThat(view.id()).isEqualTo(99L);
        assertThat(view.method()).isEqualTo("GET");
        verify(apiRepo).insert(ArgumentMatchers.any(SysApi.class));
    }

    @Test
    void softDelete_clearsBindingsAndSyncsCasbin() {
        when(apiRepo.findById(5L)).thenReturn(api(5L, "x", "GET", "/x", "x", "g"));
        when(roleBindingRepo.findRoleIdsByApiId(5L)).thenReturn(List.of(2L));
        when(userRoleRepo.findActiveUserIdsByRoleId(2L)).thenReturn(List.of(10L));
        when(apiRepo.softDeleteById(5L)).thenReturn(1L);

        ApiView view = service.softDelete(5L);

        assertThat(view.deletedAt()).isPositive();
        verify(menuApiRepo).clearByApiId(5L);
        verify(roleBindingRepo).clearApisByApiId(5L);
        verify(userService).syncCasbinForUser(10L);
    }

    @Test
    void batch_enable_updatesFlags() {
        when(apiRepo.listByIds(List.of(1L, 2L)))
                .thenReturn(List.of(api(1L, "a", "GET", "/a", "a", "g"), api(2L, "b", "GET", "/b", "b", "g")));
        when(roleBindingRepo.findRoleIdsByApiId(ArgumentMatchers.anyLong())).thenReturn(List.of());

        ApiBatchResult result = service.batch(new ApiBatchCommand("enable", List.of(1L, 2L)));

        assertThat(result.affected()).isEqualTo(2);
        verify(apiRepo).updateIsEnabled(1L, 1);
        verify(apiRepo).updateIsEnabled(2L, 1);
    }

    @Test
    void syncFromManifest_skipsExisting_addsMissing() {
        // 第一条存在，其余模拟不存在
        when(apiRepo.existsByMethodAndPath(
                        ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.isNull()))
                .thenAnswer(inv -> {
                    String path = inv.getArgument(1);
                    return "/api/auth/codes".equals(path);
                });
        when(apiRepo.existsByPermissionCode(ArgumentMatchers.anyString(), ArgumentMatchers.isNull()))
                .thenReturn(false);
        when(apiRepo.listAll()).thenReturn(List.of(api(1L, "x", "GET", "/api/auth/codes", "auth:codes", "会话")));

        ApiSyncResult result = service.syncFromManifest();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.added()).isEqualTo(ApiSyncManifest.entries().size() - 1);
        assertThat(result.total()).isEqualTo(1);
        verify(apiRepo, org.mockito.Mockito.times(ApiSyncManifest.entries().size() - 1))
                .insert(ArgumentMatchers.any(SysApi.class));
    }

    @Test
    void update_duplicatePermission_throws() {
        when(apiRepo.findById(1L)).thenReturn(api(1L, "a", "GET", "/a", "a:code", "g"));
        when(apiRepo.existsByMethodAndPath("GET", "/a", 1L)).thenReturn(false);
        when(apiRepo.existsByPermissionCode("taken", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(new UpdateApiCommand(1L, null, null, null, "taken", null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("permissionCode");
    }

    private static SysApi api(Long id, String name, String method, String path, String code, String group) {
        SysApi a = new SysApi();
        a.setId(id);
        a.setName(name);
        a.setMethod(method);
        a.setPath(path);
        a.setPermissionCode(code);
        a.setApiGroup(group);
        a.setRemark("");
        a.setIsEnabled(1);
        a.setDeletedAt(0L);
        a.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        a.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        a.setCreatedBy(0L);
        a.setUpdatedBy(0L);
        return a;
    }
}
