package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateLocaleCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新语言请求；字段 null 表示不改。
 *
 * <p>映射到 {@link UpdateLocaleCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateLocaleCommand.class)
@Schema(description = "更新 i18n 语言")
public class UpdateI18nLocaleRequest {

    @Size(max = 16)
    private String code;

    @Size(max = 64)
    private String name;

    private Integer sort;

    @Size(max = 512)
    private String remark;

    private Integer isDefault;

    private Integer isEnabled;
}
