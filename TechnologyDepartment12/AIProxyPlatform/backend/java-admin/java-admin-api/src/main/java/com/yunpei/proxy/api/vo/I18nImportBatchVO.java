package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量导入响应。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "i18n 批量导入结果")
public class I18nImportBatchVO {

    private Boolean ok;
    private Affected affected;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Affected {
        private Integer createdLocales;
        private Integer softDeleted;
        private Integer createdTranslations;
        private List<PerFile> perFile;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerFile {
        private String name;
        private Boolean ok;
        private String error;
        private Integer createdLocales;
        private Integer softDeleted;
        private Integer createdTranslations;
    }
}
