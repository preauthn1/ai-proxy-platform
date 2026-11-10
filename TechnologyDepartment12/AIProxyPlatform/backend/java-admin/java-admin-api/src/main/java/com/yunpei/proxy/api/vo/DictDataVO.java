package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.dict.DictManageModels.DictDataView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典数据 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = DictDataView.class)
@Schema(description = "字典数据")
public class DictDataVO {

    private Long id;
    private Long typeId;
    private String value;
    private String label;
    private Integer sort;
    private Integer isDefault;
    private String platform;
    private String tagType;
    private Integer isEnabled;
    private Long deletedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    /** 仅 list 接口 join 返回。 */
    private String typeCode;
}
