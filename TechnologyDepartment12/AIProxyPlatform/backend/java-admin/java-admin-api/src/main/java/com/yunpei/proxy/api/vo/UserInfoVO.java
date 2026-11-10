package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户信息 VO（与前端 UserInfo 对齐）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前登录用户信息")
public class UserInfoVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "root")
    private String username;

    @Schema(description = "展示名（昵称）", example = "Root")
    private String realName;

    @Schema(description = "角色编码列表")
    private List<String> roles;

    @Schema(description = "默认首页路径", example = "/analytics")
    private String homePath;

    @Schema(description = "头像 URL")
    private String avatar;
}
