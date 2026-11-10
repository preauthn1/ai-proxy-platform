package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语言 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = LocaleView.class)
@Schema(description = "i18n 语言")
public class I18nLocaleVO {

    private Long id;
    private String code;
    private String name;
    private Integer isDefault;
    private Integer sort;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
