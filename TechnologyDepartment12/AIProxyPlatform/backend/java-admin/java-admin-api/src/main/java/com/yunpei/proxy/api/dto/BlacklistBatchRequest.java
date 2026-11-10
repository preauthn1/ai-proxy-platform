package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 黑名单批量操作。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = BlacklistBatchCommand.class)
@Schema(description = "黑名单批量 enable|disable|delete")
public class BlacklistBatchRequest {

    @Schema(description = "enable | disable | delete", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
