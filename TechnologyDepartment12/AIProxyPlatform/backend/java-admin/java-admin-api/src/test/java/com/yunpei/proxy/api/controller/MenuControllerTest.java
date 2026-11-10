package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateMenuRequest;
import com.yunpei.proxy.api.dto.MenuApiBindRequest;
import com.yunpei.proxy.api.dto.MenuBatchRequest;
import com.yunpei.proxy.api.dto.UpdateMenuRequest;
import com.yunpei.proxy.api.vo.MenuApiBindResultVO;
import com.yunpei.proxy.api.vo.MenuBatchResultVO;
import com.yunpei.proxy.api.vo.MenuListItemVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.TreePageData;
import com.yunpei.proxy.service.menu.MenuManageModels.CreateMenuCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListPage;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListQuery;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuView;
import com.yunpei.proxy.service.menu.SysMenuService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link MenuController} 契约测试。
 *
 * <p>登录校验由 WebConfig SaInterceptor 负责，不在此重复测。
 */
class MenuControllerTest {

    private final SysMenuService sysMenuService = mock(SysMenuService.class);
    private final Converter converter = new Converter();
    private final MenuController controller = new MenuController(sysMenuService, converter);

    @Test
    void list_returnsTreePage() {
        when(sysMenuService.pageMenus(ArgumentMatchers.any(MenuListQuery.class)))
                .thenReturn(new MenuListPage(List.of(sampleView(1L, "系统")), 1L, 3L));

        Result<TreePageData<MenuListItemVO>> result = controller.list(1, 20, null, null, null, null);
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItemTotal()).isEqualTo(3L);
        assertThat(result.getData().getItems().get(0).getName()).isEqualTo("系统");
    }

    @Test
    void create_mapsBody() {
        when(sysMenuService.create(ArgumentMatchers.any(CreateMenuCommand.class)))
                .thenReturn(sampleView(10L, "用户"));
        CreateMenuRequest req = new CreateMenuRequest();
        req.setName("用户");
        req.setType("MENU");
        req.setPath("/system/user");

        Result<MenuListItemVO> result = controller.create(req);
        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateMenuCommand> cap = ArgumentCaptor.forClass(CreateMenuCommand.class);
        verify(sysMenuService).create(cap.capture());
        assertThat(cap.getValue().path()).isEqualTo("/system/user");
    }

    @Test
    void update_parentIdNull_present() {
        when(sysMenuService.update(ArgumentMatchers.any())).thenReturn(sampleView(2L, "X"));
        UpdateMenuRequest body = new UpdateMenuRequest();
        body.setParentId(null);
        body.setName("X");

        Result<MenuListItemVO> result = controller.update(2L, body);
        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<com.yunpei.proxy.service.menu.MenuManageModels.UpdateMenuCommand> cap =
                ArgumentCaptor.forClass(com.yunpei.proxy.service.menu.MenuManageModels.UpdateMenuCommand.class);
        verify(sysMenuService).update(cap.capture());
        assertThat(cap.getValue().parentId().present()).isTrue();
        assertThat(cap.getValue().parentId().value()).isNull();
    }

    @Test
    void batch_returnsAffected() {
        when(sysMenuService.batch(ArgumentMatchers.any()))
                .thenReturn(new MenuBatchResult("enable", 2, List.of(1L, 2L)));
        MenuBatchRequest req = new MenuBatchRequest();
        req.setAction("enable");
        req.setIds(List.of(1L, 2L));

        Result<MenuBatchResultVO> result = controller.batch(req);
        assertThat(result.getData().getAffected()).isEqualTo(2);
    }

    @Test
    void setMenuApis_returnsIds() {
        when(sysMenuService.setMenuApis(ArgumentMatchers.eq(5L), ArgumentMatchers.anyList()))
                .thenReturn(new MenuApiBindResult(5L, List.of(9L)));
        MenuApiBindRequest req = new MenuApiBindRequest();
        req.setApiIds(List.of(9L));

        Result<MenuApiBindResultVO> result = controller.setMenuApis(5L, req);
        assertThat(result.getData().getMenuId()).isEqualTo(5L);
        assertThat(result.getData().getApiIds()).isEqualTo(List.of(9L));
    }

    @Test
    void nameExists_delegates() {
        when(sysMenuService.nameExists("系统", 1L)).thenReturn(true);
        assertThat(controller.nameExists("系统", 1L).getData()).isTrue();
    }

    private static MenuView sampleView(Long id, String name) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new MenuView(
                id,
                null,
                name,
                "DIR",
                null,
                null,
                "",
                "",
                null,
                "/" + id + "/",
                null,
                0,
                0,
                1,
                0L,
                "",
                now,
                now,
                0L,
                0L);
    }
}
