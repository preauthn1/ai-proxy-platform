package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.api.ApiManageModels.ApiSyncResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 同步结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = ApiSyncResult.class)
@Schema(description = "API 同步结果 {added,skipped,total}")
public class ApiSyncResultVO {

    @Schema(description = "新增条数")
    private Integer added;

    @Schema(description = "已存在跳过条数")
    private Integer skipped;

    @Schema(description = "同步后未软删总数")
    private Integer total;
}
