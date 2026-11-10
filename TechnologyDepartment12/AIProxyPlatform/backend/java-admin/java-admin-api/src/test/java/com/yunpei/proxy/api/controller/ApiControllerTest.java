package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.ApiBatchRequest;
import com.yunpei.proxy.api.dto.CreateApiRequest;
import com.yunpei.proxy.api.dto.UpdateApiRequest;
import com.yunpei.proxy.api.vo.ApiBatchResultVO;
import com.yunpei.proxy.api.vo.ApiListItemVO;
import com.yunpei.proxy.api.vo.ApiSyncResultVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.TreePageData;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListPage;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListQuery;
import com.yunpei.proxy.service.api.ApiManageModels.ApiSyncResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiView;
import com.yunpei.proxy.service.api.ApiManageModels.CreateApiCommand;
import com.yunpei.proxy.service.api.ApiManageModels.UpdateApiCommand;
import com.yunpei.proxy.service.api.SysApiService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link ApiController} 契约测试。
 *
 * <p>登录校验由 WebConfig SaInterceptor 负责，不在此重复测。
 */
class ApiControllerTest {

    private final SysApiService sysApiService = mock(SysApiService.class);
    private final Converter converter = new Converter();
    private final ApiController controller = new ApiController(sysApiService, converter);

    @Test
    void list_returnsTreePageWithItemTotal() {
        when(sysApiService.pageApis(ArgumentMatchers.any(ApiListQuery.class)))
                .thenReturn(new ApiListPage(List.of(sampleView(1L, "列表")), 2L, 5L));

        Result<TreePageData<ApiListItemVO>> result = controller.list(1, 20, null, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(2L);
        assertThat(result.getData().getItemTotal()).isEqualTo(5L);
        assertThat(result.getData().getItems().get(0).getName()).isEqualTo("列表");
    }

    @Test
    void create_mapsBody() {
        when(sysApiService.create(ArgumentMatchers.any(CreateApiCommand.class))).thenReturn(sampleView(10L, "新建"));
        CreateApiRequest req = new CreateApiRequest();
        req.setName("新建");
        req.setMethod("POST");
        req.setPath("/api/x");
        req.setPermissionCode("x:create");

        Result<ApiListItemVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateApiCommand> cap = ArgumentCaptor.forClass(CreateApiCommand.class);
        verify(sysApiService).create(cap.capture());
        assertThat(cap.getValue().path()).isEqualTo("/api/x");
        assertThat(cap.getValue().permissionCode()).isEqualTo("x:create");
    }

    @Test
    void update_forwardsFields() {
        when(sysApiService.update(ArgumentMatchers.any(UpdateApiCommand.class))).thenReturn(sampleView(2L, "改"));
        UpdateApiRequest req = new UpdateApiRequest();
        req.setName("改");
        req.setIsEnabled(0);

        Result<ApiListItemVO> result = controller.update(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateApiCommand> cap = ArgumentCaptor.forClass(UpdateApiCommand.class);
        verify(sysApiService).update(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(2L);
        assertThat(cap.getValue().isEnabled()).isEqualTo(0);
    }

    @Test
    void batch_returnsAffected() {
        when(sysApiService.batch(ArgumentMatchers.any())).thenReturn(new ApiBatchResult("delete", 2, List.of(1L, 2L)));
        ApiBatchRequest req = new ApiBatchRequest();
        req.setAction("delete");
        req.setIds(List.of(1L, 2L));

        Result<ApiBatchResultVO> result = controller.batch(req);

        assertThat(result.getData().getAffected()).isEqualTo(2);
        assertThat(result.getData().getAction()).isEqualTo("delete");
    }

    @Test
    void sync_returnsCounts() {
        when(sysApiService.syncFromManifest()).thenReturn(new ApiSyncResult(3, 77, 80));

        Result<ApiSyncResultVO> result = controller.sync();

        assertThat(result.getData().getAdded()).isEqualTo(3);
        assertThat(result.getData().getSkipped()).isEqualTo(77);
        assertThat(result.getData().getTotal()).isEqualTo(80);
    }

    @Test
    void groups_returnsList() {
        when(sysApiService.listGroups()).thenReturn(List.of("会话", "用户管理"));

        Result<List<String>> result = controller.groups();

        assertThat(result.getData()).containsExactly("会话", "用户管理");
    }

    private static ApiView sampleView(Long id, String name) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new ApiView(id, name, "GET", "/api/x", "x:list", "分组", "", 1, 0L, now, now, 0L, 0L);
    }
}
