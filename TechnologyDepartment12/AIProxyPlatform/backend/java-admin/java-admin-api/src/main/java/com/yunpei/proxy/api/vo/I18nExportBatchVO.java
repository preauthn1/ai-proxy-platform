package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量导出响应。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "i18n 批量导出")
public class I18nExportBatchVO {

    private List<FileItem> files;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileItem {
        private String code;
        private String format;
        private Map<String, Object> content;
    }
}
