package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 多文件导入请求。
 *
 * @author yunpei
 */
@Data
@Schema(description = "i18n 批量导入")
public class I18nImportBatchRequest {

    private List<Item> items;

    @Data
    @Schema(description = "单文件导入项")
    public static class Item {
        private String name;
        private String prefix;
        private String localeCode;
        private String format;
        private Object payload;
    }
}
