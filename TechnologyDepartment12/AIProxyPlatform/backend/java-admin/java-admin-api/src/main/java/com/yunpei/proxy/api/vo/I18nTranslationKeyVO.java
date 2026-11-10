package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationKeyView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按 key 聚合的主行 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = TranslationKeyView.class)
@Schema(description = "i18n 翻译 key 聚合行")
public class I18nTranslationKeyVO {

    private String translationKey;
    private Integer localeCount;
    private Long sampleRowId;
    private Long sampleLocaleId;
    private String sampleLocaleCode;
    private LocalDateTime sampleUpdatedAt;
}
