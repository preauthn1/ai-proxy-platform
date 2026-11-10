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
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateLocaleCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleView;
import com.yunpei.proxy.service.repository.I18nLocaleRepository;
import com.yunpei.proxy.service.repository.I18nTranslationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link I18nLocaleService} 业务行为。
 */
class I18nLocaleServiceTest {

    private final I18nLocaleRepository localeRepo = mock(I18nLocaleRepository.class);
    private final I18nTranslationRepository translationRepo = mock(I18nTranslationRepository.class);
    private I18nLocaleService service;

    @BeforeEach
    void init() {
        service = new I18nLocaleService(localeRepo, translationRepo, new io.github.linpeilie.Converter());
    }

    @Test
    void page_mapsRows() {
        I18nLocale row = locale(1L, "zh-CN", 1);
        EasyPageResult<I18nLocale> easyPage = new EasyPageResult<>() {
            @Override
            public List<I18nLocale> getData() {
                return List.of(row);
            }

            @Override
            public long getTotal() {
                return 1L;
            }
        };
        when(localeRepo.page(1, 20, null, null, null, null)).thenReturn(easyPage);

        PageData<LocaleView> page = service.page(LocaleListQuery.of(1, 20, null, null, null));

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems().get(0).code()).isEqualTo("zh-CN");
        assertThat(page.getItems().get(0).isDefault()).isEqualTo(1);
    }

    @Test
    void create_invalidCode_throws() {
        assertThatThrownBy(() -> service.create(new CreateLocaleCommand("bad_code", "x", 0, "", 0, 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BCP-47");
        verify(localeRepo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_duplicateCode_throws() {
        when(localeRepo.existsByCode("zh-CN", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateLocaleCommand("zh-CN", "中文", 0, "", 0, 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_default_clearsOthers() {
        when(localeRepo.existsByCode("en-US", null)).thenReturn(false);
        doAnswer(inv -> {
                    I18nLocale t = inv.getArgument(0);
                    t.setId(99L);
                    t.setDeletedAt(0L);
                    t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    t.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    t.setCreatedBy(0L);
                    t.setUpdatedBy(0L);
                    return null;
                })
                .when(localeRepo)
                .insert(ArgumentMatchers.any(I18nLocale.class));
        when(localeRepo.findById(99L)).thenReturn(locale(99L, "en-US", 1));

        LocaleView view = service.create(new CreateLocaleCommand("en-US", "English", 0, "", 1, 1));

        assertThat(view.id()).isEqualTo(99L);
        verify(localeRepo).clearDefaultExcept(null);
    }

    @Test
    void softDelete_default_throws() {
        when(localeRepo.findById(1L)).thenReturn(locale(1L, "zh-CN", 1));

        assertThatThrownBy(() -> service.softDelete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("默认语言禁止删除");
    }

    @Test
    void softDelete_hasTranslations_throws() {
        when(localeRepo.findById(2L)).thenReturn(locale(2L, "en-US", 0));
        when(translationRepo.existsActiveByLocaleId(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先清空");
    }

    @Test
    void batch_delete_blocksDefault() {
        when(localeRepo.listByIds(List.of(1L))).thenReturn(List.of(locale(1L, "zh-CN", 1)));

        assertThatThrownBy(() -> service.batch(new BatchCommand("delete", List.of(1L))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("默认语言禁止删除");
    }

    @Test
    void batch_enable_ok() {
        when(localeRepo.listByIds(List.of(2L, 3L))).thenReturn(List.of(locale(2L, "en-US", 0), locale(3L, "ja-JP", 0)));
        when(localeRepo.updateIsEnabled(2L, 1)).thenReturn(1L);
        when(localeRepo.updateIsEnabled(3L, 1)).thenReturn(1L);

        BatchResult result = service.batch(new BatchCommand("enable", List.of(2L, 3L)));

        assertThat(result.affected()).isEqualTo(2);
        assertThat(result.action()).isEqualTo("enable");
    }

    private static I18nLocale locale(Long id, String code, int isDefault) {
        I18nLocale l = new I18nLocale();
        l.setId(id);
        l.setCode(code);
        l.setName(code);
        l.setIsDefault(isDefault);
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
}
