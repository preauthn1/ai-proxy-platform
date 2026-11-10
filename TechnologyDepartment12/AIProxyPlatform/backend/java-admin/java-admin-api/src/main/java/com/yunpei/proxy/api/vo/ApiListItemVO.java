package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.api.ApiManageModels.ApiView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 资源列表/详情 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = ApiView.class)
@Schema(description = "API 资源")
public class ApiListItemVO {

    private Long id;
    private String name;
    private String method;
    private String path;
    private String permissionCode;
    private String apiGroup;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
