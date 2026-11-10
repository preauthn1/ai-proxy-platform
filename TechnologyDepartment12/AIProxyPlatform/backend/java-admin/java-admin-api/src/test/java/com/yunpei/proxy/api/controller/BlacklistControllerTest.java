package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.BlacklistBatchRequest;
import com.yunpei.proxy.api.dto.CreateBlacklistRequest;
import com.yunpei.proxy.api.dto.UpdateBlacklistRequest;
import com.yunpei.proxy.api.vo.BlacklistBatchResultVO;
import com.yunpei.proxy.api.vo.BlacklistVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchResult;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistListQuery;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistView;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.CreateBlacklistCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.UpdateBlacklistCommand;
import com.yunpei.proxy.service.blacklist.BlacklistService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link BlacklistController} 契约测试（S3）。
 */
class BlacklistControllerTest {

    private final BlacklistService blacklistService = mock(BlacklistService.class);
    private final Converter converter = new Converter();
    private final BlacklistController controller = new BlacklistController(blacklistService, converter);

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 1, 10, 0);

    @Test
    void list_returnsItemsTotal() {
        when(blacklistService.page(ArgumentMatchers.any(BlacklistListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleView(1L, "IP", "1.2.3.4")), 1L));

        Result<PageData<BlacklistVO>> result = controller.list(1, 20, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItems().get(0).getTargetValue()).isEqualTo("1.2.3.4");
    }

    @Test
    void create_mapsBody() {
        when(blacklistService.create(ArgumentMatchers.any(CreateBlacklistCommand.class)))
                .thenReturn(sampleView(10L, "USER", "42"));
        CreateBlacklistRequest req = new CreateBlacklistRequest();
        req.setTargetType("USER");
        req.setTargetValue("42");
        req.setScope("LOGIN");
        req.setReason("abuse");

        Result<BlacklistVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateBlacklistCommand> cap = ArgumentCaptor.forClass(CreateBlacklistCommand.class);
        verify(blacklistService).create(cap.capture());
        assertThat(cap.getValue().targetType()).isEqualTo("USER");
        assertThat(cap.getValue().targetValue()).isEqualTo("42");
        assertThat(cap.getValue().scope()).isEqualTo("LOGIN");
        assertThat(cap.getValue().reason()).isEqualTo("abuse");
    }

    @Test
    void update_forwardsId() {
        when(blacklistService.update(ArgumentMatchers.any(UpdateBlacklistCommand.class)))
                .thenReturn(sampleView(2L, "IP", "9.9.9.9"));
        UpdateBlacklistRequest req = new UpdateBlacklistRequest();
        req.setRemark("note");

        Result<BlacklistVO> result = controller.update(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateBlacklistCommand> cap = ArgumentCaptor.forClass(UpdateBlacklistCommand.class);
        verify(blacklistService).update(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(2L);
        assertThat(cap.getValue().remark()).isEqualTo("note");
    }

    @Test
    void batch_returnsAffected() {
        when(blacklistService.batch(ArgumentMatchers.any()))
                .thenReturn(new BlacklistBatchResult("delete", 2, List.of(1L, 2L)));
        BlacklistBatchRequest req = new BlacklistBatchRequest();
        req.setAction("delete");
        req.setIds(List.of(1L, 2L));

        Result<BlacklistBatchResultVO> result = controller.batch(req);

        assertThat(result.getData().getAffected()).isEqualTo(2);
        assertThat(result.getData().getAction()).isEqualTo("delete");
    }

    @Test
    void all_returnsList() {
        when(blacklistService.listAll(ArgumentMatchers.any(BlacklistListQuery.class)))
                .thenReturn(List.of(sampleView(1L, "IP", "a"), sampleView(2L, "DEVICE", "d1")));

        Result<List<BlacklistVO>> result = controller.all(null, null, null, 1);

        assertThat(result.getData()).hasSize(2);
    }

    @Test
    void detail_returnsVo() {
        when(blacklistService.getById(7L)).thenReturn(sampleView(7L, "IP", "8.8.8.8"));

        Result<BlacklistVO> result = controller.detail(7L);

        assertThat(result.getData().getId()).isEqualTo(7L);
        assertThat(result.getData().getTargetValue()).isEqualTo("8.8.8.8");
    }

    private static BlacklistView sampleView(Long id, String type, String value) {
        return new BlacklistView(id, type, value, "ALL", "", T0, null, "", 1, 0L, T0, T0, 0L, 0L);
    }
}
