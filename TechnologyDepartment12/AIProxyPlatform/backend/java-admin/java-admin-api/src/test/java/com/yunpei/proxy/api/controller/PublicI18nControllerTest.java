package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.vo.PublicI18nVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.i18n.I18nManageModels.PublicI18nBundle;
import com.yunpei.proxy.service.i18n.I18nTranslationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PublicI18nController} 契约测试。
 */
class PublicI18nControllerTest {

    private final I18nTranslationService translationService = mock(I18nTranslationService.class);
    private final PublicI18nController controller = new PublicI18nController(translationService);

    @Test
    void getByCode_returnsFullBundle() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("common.ok", "确认");
        when(translationService.getPublicBundle("zh-CN", null)).thenReturn(PublicI18nBundle.of("abcd1234", data));

        Result<PublicI18nVO> result = controller.getByCode("zh-CN", null);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().isUnchanged()).isFalse();
        assertThat(result.getData().getHash()).isEqualTo("abcd1234");
        assertThat(result.getData().getData()).containsEntry("common.ok", "确认");
    }

    @Test
    void getByCode_whenHashMatches_returnsUnchanged() {
        when(translationService.getPublicBundle("zh-CN", "abcd1234")).thenReturn(PublicI18nBundle.noChange());

        Result<PublicI18nVO> result = controller.getByCode("zh-CN", "abcd1234");

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().isUnchanged()).isTrue();
        assertThat(result.getData().getHash()).isNull();
        assertThat(result.getData().getData()).isNull();
    }
}
