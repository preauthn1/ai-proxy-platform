package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateTranslationCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新翻译请求；字段 null 表示不改。
 *
 * <p>映射到 {@link UpdateTranslationCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateTranslationCommand.class)
@Schema(description = "更新 i18n 翻译")
public class UpdateI18nTranslationRequest {

    @Size(max = 255)
    private String translationKey;

    private String value;

    @Size(max = 512)
    private String remark;

    private Integer isEnabled;
}
