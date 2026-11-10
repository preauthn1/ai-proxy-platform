package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开 i18n 翻译包（对齐 mock {@code GET /api/public/i18n/:code}）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公开 i18n 翻译包（增量同步）")
public class PublicI18nVO {

    @Schema(description = "客户端 hash 与服务端一致时为 true")
    private boolean unchanged;

    @Schema(description = "内容 hash（SHA256 前 8 位 hex）；unchanged 时可能为空")
    private String hash;

    @Schema(description = "translationKey → value；unchanged 时可能为空")
    private Map<String, String> data;
}
