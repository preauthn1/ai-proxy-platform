package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchResult;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单批量操作结果。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = MenuBatchResult.class)
@Schema(description = "菜单批量操作结果")
public class MenuBatchResultVO {

    @Schema(description = "动作：enable|disable|delete")
    private String action;

    @Schema(description = "影响条数")
    private Integer affected;

    @Schema(description = "涉及的菜单 ID 列表")
    private List<Long> ids;
}
