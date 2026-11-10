package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateI18nLocaleRequest;
import com.yunpei.proxy.api.dto.I18nBatchRequest;
import com.yunpei.proxy.api.dto.UpdateI18nLocaleRequest;
import com.yunpei.proxy.api.vo.I18nBatchResultVO;
import com.yunpei.proxy.api.vo.I18nLocaleVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.i18n.I18nLocaleService;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateLocaleCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleView;
import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateLocaleCommand;
import com.yunpei.proxy.service.i18n.I18nTranslationService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link I18nLocaleController} 契约测试。
 */
class I18nLocaleControllerTest {

    private final I18nLocaleService localeService = mock(I18nLocaleService.class);
    private final I18nTranslationService translationService = mock(I18nTranslationService.class);
    private final Converter converter = new Converter();
    private final I18nLocaleController controller =
            new I18nLocaleController(localeService, translationService, converter);

    @Test
    void list_returnsItemsTotal() {
        when(localeService.page(ArgumentMatchers.any(LocaleListQuery.class)))
                .thenReturn(PageData.of(List.of(sampleView(1L, "zh-CN")), 1L));

        Result<PageData<I18nLocaleVO>> result = controller.list(1, 20, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getItems().get(0).getCode()).isEqualTo("zh-CN");
    }

    @Test
    void create_mapsBody() {
        when(localeService.create(ArgumentMatchers.any(CreateLocaleCommand.class)))
                .thenReturn(sampleView(10L, "en-US"));
        CreateI18nLocaleRequest req = new CreateI18nLocaleRequest();
        req.setCode("en-US");
        req.setName("English");

        Result<I18nLocaleVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateLocaleCommand> cap = ArgumentCaptor.forClass(CreateLocaleCommand.class);
        verify(localeService).create(cap.capture());
        assertThat(cap.getValue().code()).isEqualTo("en-US");
        assertThat(cap.getValue().name()).isEqualTo("English");
    }

    @Test
    void update_forwardsId() {
        when(localeService.update(ArgumentMatchers.any(UpdateLocaleCommand.class)))
                .thenReturn(sampleView(2L, "zh-CN"));
        UpdateI18nLocaleRequest req = new UpdateI18nLocaleRequest();
        req.setName("简体中文");

        Result<I18nLocaleVO> result = controller.update(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateLocaleCommand> cap = ArgumentCaptor.forClass(UpdateLocaleCommand.class);
        verify(localeService).update(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(2L);
        assertThat(cap.getValue().name()).isEqualTo("简体中文");
    }

    @Test
    void batch_returnsAffected() {
        when(localeService.batch(ArgumentMatchers.any())).thenReturn(new BatchResult("delete", 2, List.of(1L, 2L)));
        I18nBatchRequest req = new I18nBatchRequest();
        req.setAction("delete");
        req.setIds(List.of(1L, 2L));

        Result<I18nBatchResultVO> result = controller.batch(req);

        assertThat(result.getData().getAffected()).isEqualTo(2);
        assertThat(result.getData().getAction()).isEqualTo("delete");
    }

    @Test
    void all_returnsList() {
        when(localeService.listAll(ArgumentMatchers.any(LocaleListQuery.class)))
                .thenReturn(List.of(sampleView(1L, "zh-CN"), sampleView(2L, "en-US")));

        Result<List<I18nLocaleVO>> result = controller.all(null, null, 1);

        assertThat(result.getData()).hasSize(2);
    }

    private static LocaleView sampleView(Long id, String code) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new LocaleView(id, code, "名称", 0, 0, "", 1, 0L, now, now, 0L, 0L);
    }
}
