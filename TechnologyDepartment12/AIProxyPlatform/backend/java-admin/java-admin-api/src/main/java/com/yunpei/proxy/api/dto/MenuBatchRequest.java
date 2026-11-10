package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 菜单批量操作。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = MenuBatchCommand.class)
@Schema(description = "菜单批量 enable|disable|delete")
public class MenuBatchRequest {

    @Schema(description = "enable | disable | delete", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "菜单 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
