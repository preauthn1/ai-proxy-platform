package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.dict.DictManageModels.DictBatchCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 字典类型/数据批量操作。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = DictBatchCommand.class)
@Schema(description = "字典批量 enable|disable|delete")
public class DictBatchRequest {

    @Schema(description = "enable | disable | delete", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
