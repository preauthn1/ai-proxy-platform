package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按 key 聚合的多语言版本。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按 key 查询翻译")
public class I18nTranslationByKeyVO {

    private String translationKey;
    private List<I18nTranslationVO> values;
}
