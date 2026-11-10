package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入预览响应。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "i18n 导入预览")
public class I18nImportPreviewVO {

    private List<I18nTranslationVO> currentRows;
}
