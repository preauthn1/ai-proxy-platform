package com.yunpei.proxy.api.dto;

import com.yunpei.proxy.service.blacklist.BlacklistManageModels.UpdateBlacklistCommand;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 更新访问黑名单请求（字段 null 表示不改；{@code clearExpiresAt=true} 将 expiresAt 置空=永久）。
 *
 * <p>映射到 {@link UpdateBlacklistCommand} 时 {@code id} 由路径参数补全（见 Controller）。
 *
 * @author yunpei
 */
@Data
@AutoMapper(target = UpdateBlacklistCommand.class)
@Schema(description = "更新访问黑名单")
public class UpdateBlacklistRequest {

    @Size(max = 16)
    @Schema(description = "目标类型 IP|USER|DEVICE")
    private String targetType;

    @Size(max = 128)
    @Schema(description = "目标值")
    private String targetValue;

    @Size(max = 16)
    @Schema(description = "范围 LOGIN|API|ALL")
    private String scope;

    @Size(max = 512)
    private String reason;

    @Schema(description = "生效开始（含）")
    private LocalDateTime startsAt;

    @Schema(description = "生效结束（不含）")
    private LocalDateTime expiresAt;

    @Schema(description = "为 true 时将 expiresAt 清空为永久封禁")
    private Boolean clearExpiresAt;

    @Size(max = 512)
    private String remark;

    @Schema(description = "1=启用 0=禁用")
    private Integer isEnabled;
}
