package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 黑名单批量操作结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = BlacklistBatchResult.class)
@Schema(description = "黑名单批量操作结果")
public class BlacklistBatchResultVO {

    @Schema(description = "动作：enable|disable|delete")
    private String action;

    @Schema(description = "影响条数")
    private Integer affected;

    @Schema(description = "涉及的 ID 列表")
    private List<Long> ids;
}
