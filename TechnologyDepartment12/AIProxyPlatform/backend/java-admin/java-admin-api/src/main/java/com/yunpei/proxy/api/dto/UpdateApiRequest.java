package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.api.ApiManageModels.UpdateApiCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 API 资源请求；字段 null 表示不改。
 *
 * <p>映射到 {@link UpdateApiCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateApiCommand.class)
@Schema(description = "更新 API 资源")
public class UpdateApiRequest {

    @Size(max = 64)
    private String name;

    @Size(max = 8)
    private String method;

    @Size(max = 255)
    private String path;

    @Size(max = 128)
    private String permissionCode;

    @Size(max = 64)
    private String apiGroup;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用")
    private Integer isEnabled;
}
