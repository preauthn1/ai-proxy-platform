package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问黑名单 VO。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = BlacklistView.class)
@Schema(description = "访问黑名单")
public class BlacklistVO {

    private Long id;
    private String targetType;
    private String targetValue;
    private String scope;
    private String reason;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
