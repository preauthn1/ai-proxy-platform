package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 导入预览请求。
 *
 * @author yunpei
 */
@Data
@Schema(description = "i18n 导入预览")
public class I18nImportPreviewRequest {

    private List<Item> items;

    @Data
    public static class Item {
        private String localeCode;
        private List<String> keys;
    }
}
