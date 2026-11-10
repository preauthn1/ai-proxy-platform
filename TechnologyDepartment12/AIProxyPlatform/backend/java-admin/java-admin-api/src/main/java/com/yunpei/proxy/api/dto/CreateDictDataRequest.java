package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.dict.DictManageModels.CreateDictDataCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建字典数据请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateDictDataCommand.class)
@Schema(description = "创建字典数据")
public class CreateDictDataRequest {

    @NotNull
    @Schema(description = "所属字典类型 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long typeId;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "字典值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "展示标签", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "是否默认；前端 Switch 传 boolean，也可传 0|1")
    private Boolean isDefault;

    @Size(max = 32)
    @Schema(description = "平台 general|react-admin|vue-admin", example = "general")
    private String platform;

    @Size(max = 32)
    @Schema(description = "Tag 样式标识", example = "default")
    private String tagType;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;

    @Size(max = 512)
    private String remark;
}
