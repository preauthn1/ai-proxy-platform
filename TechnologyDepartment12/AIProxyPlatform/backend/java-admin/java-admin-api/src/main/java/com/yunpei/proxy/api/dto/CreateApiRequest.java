package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.api.ApiManageModels.CreateApiCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 API 资源请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateApiCommand.class)
@Schema(description = "创建 API 资源")
public class CreateApiRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "接口名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Size(max = 8)
    @Schema(description = "HTTP method", requiredMode = Schema.RequiredMode.REQUIRED, example = "GET")
    private String method;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "接口路径（可含 :id 占位）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "权限码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String permissionCode;

    @Size(max = 64)
    @Schema(description = "分组")
    private String apiGroup;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
