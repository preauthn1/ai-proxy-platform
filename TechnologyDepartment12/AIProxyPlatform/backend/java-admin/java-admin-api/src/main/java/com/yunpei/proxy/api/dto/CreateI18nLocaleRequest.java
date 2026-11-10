package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.i18n.I18nManageModels.CreateLocaleCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建语言请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateLocaleCommand.class)
@Schema(description = "创建 i18n 语言")
public class CreateI18nLocaleRequest {

    @NotBlank
    @Size(max = 16)
    @Schema(description = "语言代码 BCP-47", requiredMode = Schema.RequiredMode.REQUIRED, example = "zh-CN")
    private String code;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "展示名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    private Integer sort;

    @Size(max = 512)
    private String remark;

    @Schema(description = "是否默认 0|1")
    private Integer isDefault;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
