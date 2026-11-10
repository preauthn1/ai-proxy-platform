package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * batch-upsert-by-key 响应。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按 key 批量 upsert 结果")
public class I18nBatchUpsertByKeyVO {

    private Boolean ok;
    private Affected affected;
    private List<I18nTranslationVO> values;
    private List<ErrorItem> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Affected {
        private Integer renamed;
        private Integer created;
        private Integer updated;
        private Integer deleted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorItem {
        private String code;
        private String message;
        private Long localeId;
        private Long id;
    }
}
