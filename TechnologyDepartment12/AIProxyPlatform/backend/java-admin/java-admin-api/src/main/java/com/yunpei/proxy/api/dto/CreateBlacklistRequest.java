package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.blacklist.BlacklistManageModels.CreateBlacklistCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 创建访问黑名单请求。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = CreateBlacklistCommand.class)
@Schema(description = "创建访问黑名单")
public class CreateBlacklistRequest {

    @NotBlank
    @Size(max = 16)
    @Schema(description = "目标类型 IP|USER|DEVICE", requiredMode = Schema.RequiredMode.REQUIRED, example = "IP")
    private String targetType;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "目标值", requiredMode = Schema.RequiredMode.REQUIRED, example = "203.0.113.10")
    private String targetValue;

    @Size(max = 16)
    @Schema(description = "范围 LOGIN|API|ALL，默认 ALL", example = "ALL")
    private String scope;

    @Size(max = 512)
    @Schema(description = "封禁原因（审计可见）")
    private String reason;

    @Schema(description = "生效开始（含）；缺省为当前时间")
    private LocalDateTime startsAt;

    @Schema(description = "生效结束（不含）；null=永不过期")
    private LocalDateTime expiresAt;

    @Size(max = 512)
    @Schema(description = "内部备注")
    private String remark;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
