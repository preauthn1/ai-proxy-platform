package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.dict.DictManageModels.CreateDictTypeCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建字典类型请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateDictTypeCommand.class)
@Schema(description = "创建字典类型")
public class CreateDictTypeRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_user_sex")
    private String code;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
