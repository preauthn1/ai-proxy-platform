package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.vo.LoginLogVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.log.LogManageModels.LoginLogListQuery;
import com.yunpei.proxy.service.log.LogManageModels.LoginLogView;
import com.yunpei.proxy.service.log.LoginLogService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link LoginLogController} 契约测试。
 *
 * @author yunpei
 */
class LoginLogControllerTest {

    private final LoginLogService loginLogService = mock(LoginLogService.class);
    private final Converter converter = new Converter();
    private final LoginLogController controller = new LoginLogController(loginLogService, converter);

    @Test
    void list_returnsItemsTotal() {
        when(loginLogService.page(ArgumentMatchers.any(LoginLogListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleView(1L, "root")), 1L));

        Result<PageData<LoginLogVO>> result = controller.list(1, 20, null, null, null, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItems().get(0).getUsername()).isEqualTo("root");
        assertThat(result.getData().getItems().get(0).getLoginMethod()).isEqualTo("PASSWORD");
    }

    @Test
    void list_forwardsFiltersAndArchiveSource() {
        when(loginLogService.page(ArgumentMatchers.any(LoginLogListQuery.class)))
                .thenReturn(PageData.empty());

        controller.list(2, 10, "archive", "ro", 1, "PASSWORD", "10.0.", "2026-01-01T00:00:00", "2026-12-31T23:59:59");

        ArgumentCaptor<LoginLogListQuery> cap = ArgumentCaptor.forClass(LoginLogListQuery.class);
        verify(loginLogService).page(cap.capture());
        LoginLogListQuery q = cap.getValue();
        assertThat(q.page()).isEqualTo(2);
        assertThat(q.pageSize()).isEqualTo(10);
        assertThat(q.archive()).isTrue();
        assertThat(q.username()).isEqualTo("ro");
        assertThat(q.success()).isEqualTo(1);
        assertThat(q.loginMethod()).isEqualTo("PASSWORD");
        assertThat(q.loginIp()).isEqualTo("10.0.");
        assertThat(q.loginTimeFrom()).isNotNull();
        assertThat(q.loginTimeTo()).isNotNull();
    }

    private static LoginLogView sampleView(Long id, String username) {
        LocalDateTime now = LocalDateTime.of(2026, 3, 1, 12, 0);
        return new LoginLogView(
                id,
                username,
                1,
                "",
                200,
                1L,
                "PASSWORD",
                now,
                "127.0.0.1",
                "",
                "web-admin",
                "PC",
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
