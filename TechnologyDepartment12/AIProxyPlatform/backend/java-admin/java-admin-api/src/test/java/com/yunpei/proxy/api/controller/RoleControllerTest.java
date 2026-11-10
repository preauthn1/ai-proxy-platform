package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateRoleRequest;
import com.yunpei.proxy.api.dto.RoleApiBindRequest;
import com.yunpei.proxy.api.dto.RoleMenuBindRequest;
import com.yunpei.proxy.api.dto.UpdateRoleRequest;
import com.yunpei.proxy.api.vo.RoleApiBindItemVO;
import com.yunpei.proxy.api.vo.RoleApiBindResultVO;
import com.yunpei.proxy.api.vo.RoleListItemVO;
import com.yunpei.proxy.api.vo.RoleMenuBindItemVO;
import com.yunpei.proxy.api.vo.RoleMenuBindResultVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.role.RoleManageModels.CreateRoleCommand;
import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindView;
import com.yunpei.proxy.service.role.RoleManageModels.RoleListQuery;
import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindView;
import com.yunpei.proxy.service.role.RoleManageModels.RoleView;
import com.yunpei.proxy.service.role.RoleManageModels.UpdateRoleCommand;
import com.yunpei.proxy.service.role.SysRoleService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link RoleController} 契约：Result 形状与字段映射。
 *
 * <p>登录校验由 WebConfig SaInterceptor 负责，不在此重复测。
 */
class RoleControllerTest {

    private final SysRoleService sysRoleService = mock(SysRoleService.class);
    private final Converter converter = new Converter();
    private final RoleController controller = new RoleController(sysRoleService, converter);

    @Test
    void list_returnsItemsTotal() {
        when(sysRoleService.pageRoles(ArgumentMatchers.any(RoleListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleRole(1L, "root")), 1L));

        Result<PageData<RoleListItemVO>> result = controller.list(1, 20, "ro", null, 1);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItems()).hasSize(1);
        assertThat(result.getData().getItems().get(0).getCode()).isEqualTo("root");
        assertThat(result.getData().getItems().get(0).getUserCount()).isEqualTo(1L);
    }

    @Test
    void create_mapsBody() {
        when(sysRoleService.create(ArgumentMatchers.any(CreateRoleCommand.class)))
                .thenReturn(sampleRole(10L, "ops"));
        CreateRoleRequest req = new CreateRoleRequest();
        req.setCode("ops");
        req.setName("运维");
        req.setSort(1);

        Result<RoleListItemVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getCode()).isEqualTo("ops");
        ArgumentCaptor<CreateRoleCommand> cap = ArgumentCaptor.forClass(CreateRoleCommand.class);
        verify(sysRoleService).create(cap.capture());
        assertThat(cap.getValue().code()).isEqualTo("ops");
        assertThat(cap.getValue().name()).isEqualTo("运维");
    }

    @Test
    void update_whenParentIdOmitted_usesAbsent() {
        when(sysRoleService.update(ArgumentMatchers.any(UpdateRoleCommand.class)))
                .thenReturn(sampleRole(2L, "ops"));
        UpdateRoleRequest req = new UpdateRoleRequest();
        req.setName("运维2");
        // 不调用 setParentId → parentIdPresent=false

        controller.update(2L, req);

        ArgumentCaptor<UpdateRoleCommand> cap = ArgumentCaptor.forClass(UpdateRoleCommand.class);
        verify(sysRoleService).update(cap.capture());
        assertThat(cap.getValue().parentId().present()).isFalse();
        assertThat(cap.getValue().name()).isEqualTo("运维2");
    }

    @Test
    void update_whenParentIdExplicitNull_usesPresentNull() {
        when(sysRoleService.update(ArgumentMatchers.any(UpdateRoleCommand.class)))
                .thenReturn(sampleRole(2L, "ops"));
        UpdateRoleRequest req = new UpdateRoleRequest();
        req.setParentId(null);

        controller.update(2L, req);

        ArgumentCaptor<UpdateRoleCommand> cap = ArgumentCaptor.forClass(UpdateRoleCommand.class);
        verify(sysRoleService).update(cap.capture());
        assertThat(cap.getValue().parentId().present()).isTrue();
        assertThat(cap.getValue().parentId().value()).isNull();
    }

    @Test
    void setApis_returnsRoleIdAndApiIds() {
        when(sysRoleService.replaceApis(ArgumentMatchers.eq(2L), ArgumentMatchers.eq(List.of(10L, 11L))))
                .thenReturn(new RoleApiBindResult(2L, List.of(10L, 11L)));
        RoleApiBindRequest req = new RoleApiBindRequest();
        req.setApiIds(List.of(10L, 11L));

        Result<RoleApiBindResultVO> result = controller.setApis(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getRoleId()).isEqualTo(2L);
        assertThat(result.getData().getApiIds()).isEqualTo(List.of(10L, 11L));
    }

    @Test
    void setMenus_returnsRoleIdAndMenuIds() {
        when(sysRoleService.replaceMenus(ArgumentMatchers.eq(2L), ArgumentMatchers.eq(List.of(1L))))
                .thenReturn(new RoleMenuBindResult(2L, List.of(1L)));
        RoleMenuBindRequest req = new RoleMenuBindRequest();
        req.setMenuIds(List.of(1L));

        Result<RoleMenuBindResultVO> result = controller.setMenus(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getRoleId()).isEqualTo(2L);
    }

    @Test
    void menus_mapsBoundFlag() {
        when(sysRoleService.listMenuBinds(2L))
                .thenReturn(List.of(new RoleMenuBindView(
                        1L,
                        null,
                        "系统",
                        "DIR",
                        "/system",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0,
                        1,
                        "",
                        0L,
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        true)));

        Result<List<RoleMenuBindItemVO>> result = controller.menus(2L);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).isBound()).isTrue();
        assertThat(result.getData().get(0).getName()).isEqualTo("系统");
    }

    @Test
    void apis_mapsBoundFlag() {
        when(sysRoleService.listApiBinds(2L))
                .thenReturn(List.of(new RoleApiBindView(10L, "List", "GET", "/api/x", "p", "g", 1, false)));

        Result<List<RoleApiBindItemVO>> result = controller.apis(2L);

        assertThat(result.getData().get(0).isBound()).isFalse();
        assertThat(result.getData().get(0).getMethod()).isEqualTo("GET");
    }

    private static RoleView sampleRole(Long id, String code) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        return new RoleView(id, code, "Name", null, 0, "", 1, 0L, now, now, 0L, 0L, 1L, null);
    }
}
