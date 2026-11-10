package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.dto.CreateI18nTranslationRequest;
import com.yunpei.proxy.api.dto.I18nBatchUpsertByKeyRequest;
import com.yunpei.proxy.api.dto.UpdateI18nTranslationRequest;
import com.yunpei.proxy.api.vo.I18nBatchUpsertByKeyVO;
import com.yunpei.proxy.api.vo.I18nTranslationByKeyVO;
import com.yunpei.proxy.api.vo.I18nTranslationVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertAffected;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateTranslationCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationByKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationView;
import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateTranslationCommand;
import com.yunpei.proxy.service.i18n.I18nTranslationService;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * {@link I18nTranslationController} 契约测试。
 */
class I18nTranslationControllerTest {

    private final I18nTranslationService translationService = mock(I18nTranslationService.class);
    private final Converter converter = new Converter();
    private final I18nTranslationController controller = new I18nTranslationController(translationService, converter);

    @Test
    void list_returnsRows() {
        PageData<?> page = PageData.of(List.of(sampleView(1L, "common.ok")), 1L);
        doReturn(page).when(translationService).page(ArgumentMatchers.any(TranslationListQuery.class));

        Result<PageData<?>> result = controller.list(1, 20, null, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
    }

    @Test
    void list_byKey_returnsAggregated() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        PageData<?> page = PageData.of(List.of(new TranslationKeyView("common.ok", 2, 1L, 1L, "zh-CN", now)), 1L);
        doReturn(page).when(translationService).page(ArgumentMatchers.any(TranslationListQuery.class));

        Result<PageData<?>> result = controller.list(1, 20, null, null, null, null, "true");

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getItems()).hasSize(1);
    }

    @Test
    void create_mapsBody() {
        when(translationService.create(ArgumentMatchers.any(CreateTranslationCommand.class)))
                .thenReturn(sampleView(10L, "common.save"));
        CreateI18nTranslationRequest req = new CreateI18nTranslationRequest();
        req.setLocaleId(1L);
        req.setTranslationKey("common.save");
        req.setValue("保存");

        Result<I18nTranslationVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<CreateTranslationCommand> cap = ArgumentCaptor.forClass(CreateTranslationCommand.class);
        verify(translationService).create(cap.capture());
        assertThat(cap.getValue().translationKey()).isEqualTo("common.save");
        assertThat(cap.getValue().localeId()).isEqualTo(1L);
    }

    @Test
    void update_forwardsId() {
        when(translationService.update(ArgumentMatchers.any(UpdateTranslationCommand.class)))
                .thenReturn(sampleView(2L, "common.ok"));
        UpdateI18nTranslationRequest req = new UpdateI18nTranslationRequest();
        req.setValue("确认");

        Result<I18nTranslationVO> result = controller.update(2L, req);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<UpdateTranslationCommand> cap = ArgumentCaptor.forClass(UpdateTranslationCommand.class);
        verify(translationService).update(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(2L);
        assertThat(cap.getValue().value()).isEqualTo("确认");
    }

    @Test
    void byKey_returnsValues() {
        when(translationService.getByKey("common.ok"))
                .thenReturn(new TranslationByKeyView("common.ok", List.of(sampleView(1L, "common.ok"))));

        Result<I18nTranslationByKeyVO> result = controller.byKey("common.ok");

        assertThat(result.getData().getTranslationKey()).isEqualTo("common.ok");
        assertThat(result.getData().getValues()).hasSize(1);
    }

    @Test
    void batchUpsertByKey_returnsOk() {
        when(translationService.batchUpsertByKey(ArgumentMatchers.any(BatchUpsertByKeyCommand.class)))
                .thenReturn(new BatchUpsertByKeyResult(
                        true, new BatchUpsertAffected(0, 1, 0, 0), List.of(sampleView(1L, "k")), null));
        I18nBatchUpsertByKeyRequest req = new I18nBatchUpsertByKeyRequest();
        req.setTranslationKey("k");
        I18nBatchUpsertByKeyRequest.Item item = new I18nBatchUpsertByKeyRequest.Item();
        item.setLocaleId(1L);
        item.setValue("v");
        req.setItems(List.of(item));

        Result<I18nBatchUpsertByKeyVO> result = controller.batchUpsertByKey(req);

        assertThat(result.getData().getOk()).isTrue();
        assertThat(result.getData().getAffected().getCreated()).isEqualTo(1);
    }

    private static TranslationView sampleView(Long id, String key) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new TranslationView(id, 1L, key, "值", "", 1, 0L, now, now, 0L, 0L, "zh-CN");
    }
}
