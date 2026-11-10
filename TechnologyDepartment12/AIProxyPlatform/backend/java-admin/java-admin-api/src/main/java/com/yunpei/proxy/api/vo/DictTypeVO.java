package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.dict.DictManageModels.DictTypeView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典类型 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = DictTypeView.class)
@Schema(description = "字典类型")
public class DictTypeVO {

    private Long id;
    private String code;
    private String name;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
