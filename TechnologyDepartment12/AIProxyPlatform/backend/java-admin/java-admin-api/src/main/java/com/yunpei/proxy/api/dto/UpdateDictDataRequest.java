package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictDataCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新字典数据请求（字段 null 表示不改）。
 *
 * <p>映射到 {@link UpdateDictDataCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateDictDataCommand.class)
@Schema(description = "更新字典数据")
public class UpdateDictDataRequest {

    @Size(max = 64)
    private String value;

    @Size(max = 128)
    private String label;

    private Integer sort;

    @Schema(description = "是否默认 0|1")
    private Integer isDefault;

    @Size(max = 32)
    @Schema(description = "平台 general|react-admin|vue-admin")
    private String platform;

    @Size(max = 32)
    @Schema(description = "Tag 样式标识")
    private String tagType;

    @Schema(description = "1=启用 0=禁用")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;
}
