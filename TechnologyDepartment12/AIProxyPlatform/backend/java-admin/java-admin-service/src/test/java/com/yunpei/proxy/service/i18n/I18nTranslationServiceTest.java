package com.yunpei.proxy.service.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.service.entity.I18nLocale;
import com.yunpei.proxy.service.entity.I18nTranslation;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateTranslationCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationByKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationView;
import com.yunpei.proxy.service.repository.I18nLocaleRepository;
import com.yunpei.proxy.service.repository.I18nTranslationRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link I18nTranslationService} 业务行为。
 */
class I18nTranslationServiceTest {

    private final I18nTranslationRepository translationRepo = mock(I18nTranslationRepository.class);
    private final I18nLocaleRepository localeRepo = mock(I18nLocaleRepository.class);
    private I18nTranslationService service;

    @BeforeEach
    void init() {
        service = new I18nTranslationService(translationRepo, localeRepo, new io.github.linpeilie.Converter());
    }

    @Test
    void page_mapsRowsWithLocaleCode() {
        I18nTranslation row = translation(1L, 1L, "common.ok", "确认");
        EasyPageResult<I18nTranslation> easyPage = new EasyPageResult<>() {
            @Override
            public List<I18nTranslation> getData() {
                return List.of(row);
            }

            @Override
            public long getTotal() {
                return 1L;
            }
        };
        when(translationRepo.page(1, 20, null, null, null)).thenReturn(easyPage);
        when(localeRepo.listByIds(List.of(1L))).thenReturn(List.of(locale(1L, "zh-CN")));

        @SuppressWarnings("unchecked")
        PageData<TranslationView> page =
                (PageData<TranslationView>) service.page(TranslationListQuery.of(1, 20, null, null, null, null, null));

        assertThat(page.getItems().get(0).localeCode()).isEqualTo("zh-CN");
        assertThat(page.getItems().get(0).translationKey()).isEqualTo("common.ok");
    }

    @Test
    void page_byKey_aggregates() {
        when(translationRepo.listFiltered(null, null, null))
                .thenReturn(List.of(
                        translation(1L, 1L, "common.ok", "确认"),
                        translation(2L, 2L, "common.ok", "OK"),
                        translation(3L, 1L, "common.cancel", "取消")));
        when(localeRepo.listByIds(ArgumentMatchers.anyList()))
                .thenReturn(List.of(locale(1L, "zh-CN"), locale(2L, "en-US")));

        @SuppressWarnings("unchecked")
        PageData<TranslationKeyView> page = (PageData<TranslationKeyView>)
                service.page(TranslationListQuery.of(1, 20, null, null, null, null, "true"));

        assertThat(page.getTotal()).isEqualTo(2L);
        TranslationKeyView ok = page.getItems().stream()
                .filter(k -> "common.ok".equals(k.translationKey()))
                .findFirst()
                .orElseThrow();
        assertThat(ok.localeCount()).isEqualTo(2);
    }

    @Test
    void create_missingLocale_throws() {
        when(localeRepo.findById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(new CreateTranslationCommand(9L, "k", "v", "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not found");
        verify(translationRepo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_success() {
        when(localeRepo.findById(1L)).thenReturn(locale(1L, "zh-CN"));
        when(translationRepo.existsByLocaleAndKey(1L, "common.save", null)).thenReturn(false);
        doAnswer(inv -> {
                    I18nTranslation t = inv.getArgument(0);
                    t.setId(50L);
                    t.setDeletedAt(0L);
                    t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    t.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    t.setCreatedBy(0L);
                    t.setUpdatedBy(0L);
                    return null;
                })
                .when(translationRepo)
                .insert(ArgumentMatchers.any(I18nTranslation.class));
        when(translationRepo.findById(50L)).thenReturn(translation(50L, 1L, "common.save", "保存"));

        TranslationView view = service.create(new CreateTranslationCommand(1L, "common.save", "保存", "", 1));

        assertThat(view.id()).isEqualTo(50L);
        assertThat(view.localeCode()).isEqualTo("zh-CN");
    }

    @Test
    void getByKey_emptyValuesWhenMissing() {
        when(translationRepo.listByTranslationKey("missing")).thenReturn(List.of());

        TranslationByKeyView view = service.getByKey("missing");

        assertThat(view.translationKey()).isEqualTo("missing");
        assertThat(view.values()).isEmpty();
    }

    @Test
    void batchUpsert_createAndUpdate() {
        when(localeRepo.findById(1L)).thenReturn(locale(1L, "zh-CN"));
        when(localeRepo.findById(2L)).thenReturn(locale(2L, "en-US"));
        when(translationRepo.findByLocaleAndKey(1L, "common.ok")).thenReturn(translation(1L, 1L, "common.ok", "旧"));
        when(translationRepo.findByLocaleAndKey(2L, "common.ok")).thenReturn(null);
        when(translationRepo.listByTranslationKey("common.ok"))
                .thenReturn(List.of(translation(1L, 1L, "common.ok", "确认"), translation(99L, 2L, "common.ok", "OK")));
        when(localeRepo.listByIds(ArgumentMatchers.anyList()))
                .thenReturn(List.of(locale(1L, "zh-CN"), locale(2L, "en-US")));
        doAnswer(inv -> {
                    I18nTranslation t = inv.getArgument(0);
                    t.setId(99L);
                    return null;
                })
                .when(translationRepo)
                .insert(ArgumentMatchers.any(I18nTranslation.class));

        BatchUpsertByKeyResult result = service.batchUpsertByKey(new BatchUpsertByKeyCommand(
                "common.ok",
                null,
                List.of(new BatchUpsertItem(1L, "确认", "", 1), new BatchUpsertItem(2L, "OK", "", 1)),
                List.of()));

        assertThat(result.ok()).isTrue();
        assertThat(result.affected().updated()).isEqualTo(1);
        assertThat(result.affected().created()).isEqualTo(1);
        verify(translationRepo).update(ArgumentMatchers.any(I18nTranslation.class));
    }

    @Test
    void importPreview_returnsMatchingRows() {
        when(localeRepo.listAllActive()).thenReturn(List.of(locale(1L, "zh-CN")));
        when(translationRepo.listByLocaleIdsAndKeys(ArgumentMatchers.anyCollection(), ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(translation(1L, 1L, "common.ok", "确认")));

        ImportPreviewResult result = service.importPreview(
                new ImportPreviewCommand(List.of(new ImportPreviewItem("zh-CN", List.of("common.ok")))));

        assertThat(result.currentRows()).hasSize(1);
        assertThat(result.currentRows().get(0).localeCode()).isEqualTo("zh-CN");
    }

    @Test
    void importBatch_simpleCreatesLocaleAndTranslation() {
        when(localeRepo.findByCode("fr-FR")).thenReturn(null).thenReturn(locale(10L, "fr-FR"));
        doAnswer(inv -> {
                    I18nLocale l = inv.getArgument(0);
                    l.setId(10L);
                    return null;
                })
                .when(localeRepo)
                .insert(ArgumentMatchers.any(I18nLocale.class));
        when(translationRepo.findByLocaleAndKey(10L, "common.ok")).thenReturn(null);
        doAnswer(inv -> {
                    I18nTranslation t = inv.getArgument(0);
                    t.setId(100L);
                    return null;
                })
                .when(translationRepo)
                .insert(ArgumentMatchers.any(I18nTranslation.class));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@type", "simple");
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("ok", "OK");
        payload.put("common", common);

        ImportBatchResult result = service.importBatch(
                new ImportBatchCommand(List.of(new ImportBatchItem("fr.json", null, "fr-FR", "simple", payload))));

        assertThat(result.ok()).isTrue();
        assertThat(result.affected().createdLocales()).isEqualTo(1);
        assertThat(result.affected().createdTranslations()).isEqualTo(1);
    }

    @Test
    void listByLocaleCode_notFound_throws() {
        when(localeRepo.findByCode("xx-YY")).thenReturn(null);

        assertThatThrownBy(() -> service.listByLocaleCode("xx-YY"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getPublicBundle_returnsKvAndHash() {
        when(localeRepo.findByCode("zh-CN")).thenReturn(locale(1L, "zh-CN"));
        when(translationRepo.listEnabledByLocaleId(1L))
                .thenReturn(
                        List.of(translation(1L, 1L, "common.ok", "确认"), translation(2L, 1L, "common.cancel", "取消")));

        var bundle = service.getPublicBundle("zh-CN", null);

        assertThat(bundle.unchanged()).isFalse();
        assertThat(bundle.data()).containsEntry("common.ok", "确认").containsEntry("common.cancel", "取消");
        assertThat(bundle.hash()).isEqualTo(I18nTranslationService.computeI18nHash(bundle.data()));
    }

    @Test
    void getPublicBundle_sameHash_returnsUnchanged() {
        when(localeRepo.findByCode("zh-CN")).thenReturn(locale(1L, "zh-CN"));
        when(translationRepo.listEnabledByLocaleId(1L)).thenReturn(List.of(translation(1L, 1L, "common.ok", "确认")));

        var first = service.getPublicBundle("zh-CN", null);
        var second = service.getPublicBundle("zh-CN", first.hash());

        assertThat(second.unchanged()).isTrue();
        assertThat(second.data()).isNull();
        assertThat(second.hash()).isNull();
    }

    @Test
    void computeI18nHash_stableSorted() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("b", "2");
        a.put("a", "1");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("a", "1");
        b.put("b", "2");
        assertThat(I18nTranslationService.computeI18nHash(a))
                .isEqualTo(I18nTranslationService.computeI18nHash(b))
                .hasSize(8);
    }

    private static I18nLocale locale(Long id, String code) {
        I18nLocale l = new I18nLocale();
        l.setId(id);
        l.setCode(code);
        l.setName(code);
        l.setIsDefault(0);
        l.setSort(0);
        l.setRemark("");
        l.setIsEnabled(1);
        l.setDeletedAt(0L);
        l.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        l.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        l.setCreatedBy(0L);
        l.setUpdatedBy(0L);
        return l;
    }

    private static I18nTranslation translation(Long id, Long localeId, String key, String value) {
        I18nTranslation t = new I18nTranslation();
        t.setId(id);
        t.setLocaleId(localeId);
        t.setTranslationKey(key);
        t.setValue(value);
        t.setRemark("");
        t.setIsEnabled(1);
        t.setDeletedAt(0L);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        t.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        t.setCreatedBy(0L);
        t.setUpdatedBy(0L);
        return t;
    }
}
