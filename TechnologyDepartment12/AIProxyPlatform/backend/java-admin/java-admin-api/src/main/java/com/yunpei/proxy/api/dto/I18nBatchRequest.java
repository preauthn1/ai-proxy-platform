package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.i18n.I18nManageModels.BatchCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * i18n 批量 enable|disable|delete。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = BatchCommand.class)
@Schema(description = "i18n 批量操作")
public class I18nBatchRequest {

    @Schema(description = "enable | disable | delete", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
