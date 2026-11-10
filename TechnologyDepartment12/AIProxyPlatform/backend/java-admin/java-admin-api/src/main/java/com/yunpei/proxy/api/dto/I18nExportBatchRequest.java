package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 批量导出语言+翻译。
 *
 * @author yunpei
 */
@Data
@Schema(description = "i18n 批量导出")
public class I18nExportBatchRequest {

    @Schema(description = "语言 id 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "raw | simple", example = "simple")
    private String format;
}
