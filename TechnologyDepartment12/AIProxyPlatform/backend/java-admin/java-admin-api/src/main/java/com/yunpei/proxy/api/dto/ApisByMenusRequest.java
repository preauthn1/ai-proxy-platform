package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 按菜单聚合 API。
 *
 * @author yunpei
 */
@Data
@Schema(description = "按菜单 ID 聚合 API")
public class ApisByMenusRequest {

    @Schema(description = "菜单 ID 列表")
    private List<Long> menuIds;
}
