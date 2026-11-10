package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateDictDataRequest;
import com.yunpei.proxy.api.dto.DictBatchRequest;
import com.yunpei.proxy.api.dto.UpdateDictDataRequest;
import com.yunpei.proxy.api.vo.DictBatchResultVO;
import com.yunpei.proxy.api.vo.DictDataVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.dict.DictDataService;
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictDataCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchResult;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataView;
import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictDataCommand;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link DictDataController} 契约测试（含 platform 维度参数转发）。
 */
class DictDataControllerTest {

    private final DictDataService dictDataService = mock(DictDataService.class);
    private final Converter converter = new Converter();
    private final DictDataController controller = new DictDataController(dictDataService, converter);

    @Test
    void list_forwardsPlatformAndIncludeGeneral() {
        when(dictDataService.page(ArgumentMatchers.any(DictDataListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleView(1L, "react-admin")), 1L));

        Result<PageData<DictDataVO>> result = controller.list(1, 20, null, null, null, null, null, "react-admin", true);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<DictDataListQuery> cap = ArgumentCaptor.forClass(DictDataListQuery.class);
        verify(dictDataService).page(cap.capture());
        assertThat(cap.getValue().platform()).isEqualTo("react-admin");
        assertThat(cap.getValue().includeGeneral()).isTrue();
        assertThat(result.getData().getItems().get(0).getPlatform()).isEqualTo("react-admin");
        assertThat(result.getData().getItems().get(0).getTypeCode()).isEqualTo("sys_yes_no");
    }

    @Test
    void byType_returnsEnabledItems() {
        when(dictDataService.listByTypeCode("sys_yes_no")).thenReturn(List.of(sampleView(1L, "general")));

        Result<List<DictDataVO>> result = controller.byType("sys_yes_no");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getValue()).isEqualTo("Y");
    }

    @Test
    void create_mapsPlatform() {
        when(dictDataService.create(ArgumentMatchers.any(CreateDictDataCommand.class)))
                .thenReturn(sampleView(9L, "vue-admin"));
        CreateDictDataRequest req = new CreateDictDataRequest();
        req.setTypeId(1L);
        req.setValue("Y");
        req.setLabel("是");
        req.setPlatform("vue-admin");

        Result<DictDataVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateDictDataCommand> cap = ArgumentCaptor.forClass(CreateDictDataCommand.class);
        verify(dictDataService).create(cap.capture());
        assertThat(cap.getValue().platform()).isEqualTo("vue-admin");
    }

    @Test
    void update_forwardsPlatform() {
        when(dictDataService.update(ArgumentMatchers.any(UpdateDictDataCommand.class)))
                .thenReturn(sampleView(2L, "react-admin"));
        UpdateDictDataRequest req = new UpdateDictDataRequest();
        req.setPlatform("react-admin");

        Result<DictDataVO> updateResult = controller.update(2L, req);

        assertThat(updateResult.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateDictDataCommand> cap = ArgumentCaptor.forClass(UpdateDictDataCommand.class);
        verify(dictDataService).update(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(2L);
        assertThat(cap.getValue().platform()).isEqualTo("react-admin");
    }

    @Test
    void batch_returnsAffected() {
        when(dictDataService.batch(ArgumentMatchers.any())).thenReturn(new DictBatchResult("enable", 1, List.of(3L)));
        DictBatchRequest req = new DictBatchRequest();
        req.setAction("enable");
        req.setIds(List.of(3L));

        Result<DictBatchResultVO> result = controller.batch(req);

        assertThat(result.getData().getAffected()).isEqualTo(1);
    }

    private static DictDataView sampleView(Long id, String platform) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new DictDataView(id, 1L, "Y", "是", 0, 0, platform, "default", 1, 0L, "", now, now, 0L, 0L, "sys_yes_no");
    }
}
