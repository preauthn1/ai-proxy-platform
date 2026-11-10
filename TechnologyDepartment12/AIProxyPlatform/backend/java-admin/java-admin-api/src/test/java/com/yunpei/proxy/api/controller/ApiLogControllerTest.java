package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.vo.ApiLogVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.log.ApiLogService;
import com.yunpei.proxy.service.log.LogManageModels.ApiLogListQuery;
import com.yunpei.proxy.service.log.LogManageModels.ApiLogView;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link ApiLogController} 契约测试。
 *
 * @author yunpei
 */
class ApiLogControllerTest {

    private final ApiLogService apiLogService = mock(ApiLogService.class);
    private final Converter converter = new Converter();
    private final ApiLogController controller = new ApiLogController(apiLogService, converter);

    @Test
    void list_returnsItemsTotalWithKeyFields() {
        when(apiLogService.page(ArgumentMatchers.any(ApiLogListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleView(9L)), 1L));

        Result<PageData<ApiLogVO>> result =
                controller.list(1, 20, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        ApiLogVO item = result.getData().getItems().get(0);
        assertThat(item.getPath()).isEqualTo("/api/system/user/list");
        assertThat(item.getRequestId()).isEqualTo("req-1");
        assertThat(item.getCostTime()).isEqualTo(42L);
        assertThat(item.getUsername()).isEqualTo("root");
        assertThat(item.getStatusCode()).isEqualTo(200);
    }

    @Test
    void list_forwardsFilters() {
        when(apiLogService.page(ArgumentMatchers.any(ApiLogListQuery.class))).thenReturn(PageData.empty());

        controller.list(
                1,
                20,
                "hot",
                "GET",
                "user",
                "/api/system/user",
                1,
                200,
                "root",
                "10.0",
                "req-",
                "2026-01-01T00:00:00Z",
                "2026-12-31T23:59:59Z");

        ArgumentCaptor<ApiLogListQuery> cap = ArgumentCaptor.forClass(ApiLogListQuery.class);
        verify(apiLogService).page(cap.capture());
        ApiLogListQuery q = cap.getValue();
        assertThat(q.archive()).isFalse();
        assertThat(q.method()).isEqualTo("GET");
        assertThat(q.module()).isEqualTo("user");
        assertThat(q.path()).isEqualTo("/api/system/user");
        assertThat(q.success()).isEqualTo(1);
        assertThat(q.statusCode()).isEqualTo(200);
        assertThat(q.username()).isEqualTo("root");
        assertThat(q.clientIp()).isEqualTo("10.0");
        assertThat(q.requestId()).isEqualTo("req-");
        assertThat(q.createdAtFrom()).isNotNull();
        assertThat(q.createdAtTo()).isNotNull();
    }

    private static ApiLogView sampleView(Long id) {
        LocalDateTime now = LocalDateTime.of(2026, 3, 1, 12, 0);
        return new ApiLogView(
                id,
                "GET",
                "user",
                "/api/system/user/list",
                200,
                1,
                "",
                42L,
                "req-1",
                1L,
                "root",
                "/api/system/user/list?page=1",
                "page=1",
                "",
                "{}",
                "",
                "{\"code\":0}",
                "",
                "",
                "",
                "web-admin",
                "Web Admin",
                "10.0.0.1",
                "JUnit",
                "Unknown",
                "",
                "",
                "",
                "本机",
                now,
                null);
    }
}
