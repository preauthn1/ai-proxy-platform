package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下拉选项（label / value），用于任务配置表单等。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "下拉选项")
public class TaskOptionVO {

    @Schema(description = "展示文案", example = "LogCountTickWorkflow")
    private String label;

    @Schema(description = "提交值", example = "LogCountTickWorkflow")
    private String value;
}
