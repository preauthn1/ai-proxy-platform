package com.yunpei.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 单 key 多语言事务化 upsert 请求。
 *
 * @author yunpei
 */
@Data
@Schema(description = "按 key 批量 upsert 翻译")
public class I18nBatchUpsertByKeyRequest {

    @Schema(description = "原 translationKey", requiredMode = Schema.RequiredMode.REQUIRED)
    private String translationKey;

    @Schema(description = "可选：改名后的 key")
    private String newTranslationKey;

    @Schema(description = "要写入的语言行")
    private List<Item> items;

    @Schema(description = "随本次保存一起删除的 row id")
    private List<Long> deletedIds;

    @Data
    @Schema(description = "单语言 upsert 项")
    public static class Item {
        private Long localeId;
        private String value;
        private String remark;
        private Integer isEnabled;
    }
}
