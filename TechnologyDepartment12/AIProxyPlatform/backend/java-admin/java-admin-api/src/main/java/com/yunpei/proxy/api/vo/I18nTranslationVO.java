package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 翻译 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = TranslationView.class)
@Schema(description = "i18n 翻译")
public class I18nTranslationVO {

    private Long id;
    private Long localeId;
    private String translationKey;
    private String value;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    /** 仅 list/by-locale 等 join 后返回。 */
    private String localeCode;
}
