package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.i18n.I18nManageModels.CreateTranslationCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建翻译请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateTranslationCommand.class)
@Schema(description = "创建 i18n 翻译")
public class CreateI18nTranslationRequest {

    @NotNull
    @Schema(description = "语言 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long localeId;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "翻译键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String translationKey;

    @NotBlank
    @Schema(description = "翻译值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
