package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 批量操作结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = ApiBatchResult.class)
@Schema(description = "API 批量操作结果")
public class ApiBatchResultVO {

    @Schema(description = "动作：enable|disable|delete")
    private String action;

    @Schema(description = "影响条数")
    private Integer affected;

    @Schema(description = "涉及的 API ID 列表")
    private List<Long> ids;
}
