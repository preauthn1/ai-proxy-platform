package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictTypeCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新字典类型请求（字段 null 表示不改）。
 *
 * <p>映射到 {@link UpdateDictTypeCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateDictTypeCommand.class)
@Schema(description = "更新字典类型")
public class UpdateDictTypeRequest {

    @Size(max = 64)
    private String code;

    @Size(max = 64)
    private String name;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用")
    private Integer isEnabled;
}
