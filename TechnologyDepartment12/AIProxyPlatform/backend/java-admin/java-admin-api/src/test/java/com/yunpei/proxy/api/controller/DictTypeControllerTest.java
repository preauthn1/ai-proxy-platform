package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateDictTypeRequest;
import com.yunpei.proxy.api.dto.DictBatchRequest;
import com.yunpei.proxy.api.dto.UpdateDictTypeRequest;
import com.yunpei.proxy.api.vo.DictBatchResultVO;
import com.yunpei.proxy.api.vo.DictTypeVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictTypeCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchResult;
import com.yunpei.proxy.service.dict.DictManageModels.DictTypeListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictTypeView;
import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictTypeCommand;
import com.yunpei.proxy.service.dict.DictTypeService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link DictTypeController} 契约测试。
 */
class DictTypeControllerTest {

    private final DictTypeService dictTypeService = mock(DictTypeService.class);
    private final Converter converter = new Converter();
    private final DictTypeController controller = new DictTypeController(dictTypeService, converter);

    @Test
    void list_returnsItemsTotal() {
        when(dictTypeService.page(ArgumentMatchers.any(DictTypeListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleView(1L, "sys_yes_no")), 1L));

        Result<PageData<DictTypeVO>> result = controller.list(1, 20, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItems().get(0).getCode()).isEqualTo("sys_yes_no");
    }

    @Test
    void create_mapsBody() {
        when(dictTypeService.create(ArgumentMatchers.any(CreateDictTypeCommand.class)))
                .thenReturn(sampleView(10L, "sys_demo"));
        CreateDictTypeRequest req = new CreateDictTypeRequest();
        req.setCode("sys_demo");
        req.setName("演示");

        Result<DictTypeVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateDictTypeCommand> cap = ArgumentCaptor.forClass(CreateDictTypeCommand.class);
        verify(dictTypeService).create(cap.capture());
        assertThat(cap.getValue().code()).isEqualTo("sys_demo");
        assertThat(cap.getValue().name()).isEqualTo("演示");
    }

    @Test
    void update_forwardsId() {
        when(dictTypeService.update(ArgumentMatchers.any(UpdateDictTypeCommand.class)))
                .thenReturn(sampleView(2L, "sys_yes_no"));
        UpdateDictTypeRequest req = new UpdateDictTypeRequest();
        req.setName("是否");

        Result<DictTypeVO> result = controller.update(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateDictTypeCommand> cap = ArgumentCaptor.forClass(UpdateDictTypeCommand.class);
        verify(dictTypeService).update(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(2L);
        assertThat(cap.getValue().name()).isEqualTo("是否");
    }

    @Test
    void batch_returnsAffected() {
        when(dictTypeService.batch(ArgumentMatchers.any()))
                .thenReturn(new DictBatchResult("delete", 2, List.of(1L, 2L)));
        DictBatchRequest req = new DictBatchRequest();
        req.setAction("delete");
        req.setIds(List.of(1L, 2L));

        Result<DictBatchResultVO> result = controller.batch(req);

        assertThat(result.getData().getAffected()).isEqualTo(2);
        assertThat(result.getData().getAction()).isEqualTo("delete");
    }

    @Test
    void all_returnsList() {
        when(dictTypeService.listAll(ArgumentMatchers.any(DictTypeListQuery.class)))
                .thenReturn(List.of(sampleView(1L, "a"), sampleView(2L, "b")));

        Result<List<DictTypeVO>> result = controller.all(null, null, 1);

        assertThat(result.getData()).hasSize(2);
    }

    private static DictTypeView sampleView(Long id, String code) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new DictTypeView(id, code, "名称", "", 1, 0L, now, now, 0L, 0L);
    }
}
