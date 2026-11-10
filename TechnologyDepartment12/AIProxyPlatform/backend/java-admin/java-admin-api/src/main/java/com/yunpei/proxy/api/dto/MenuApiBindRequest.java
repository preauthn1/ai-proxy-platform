package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 菜单-API 全量绑定。
 *
 * @author yunpei
 */
@Data
@Schema(description = "菜单绑定 API")
public class MenuApiBindRequest {

    @Schema(description = "API ID 列表（全量替换）")
    private List<Long> apiIds;
}
