package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * API 资源批量操作。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = ApiBatchCommand.class)
@Schema(description = "API 批量 enable|disable|delete")
public class ApiBatchRequest {

    @Schema(description = "enable | disable | delete", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "API ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
