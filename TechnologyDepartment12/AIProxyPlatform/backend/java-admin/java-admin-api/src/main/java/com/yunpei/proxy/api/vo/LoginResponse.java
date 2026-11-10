package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 VO（字段名与前端契约对齐：accessToken + 用户摘要）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录成功响应")
public class LoginResponse {

    @Schema(description = "访问令牌（Sa-Token）；前端写入 Authorization: Bearer <token>")
    private String accessToken;

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

    /**
     * 本次登录会话专属 RSA 公钥（X.509 SPKI base64）。
     * 登录成功后客户端应用此公钥加密 AES 会话密钥；服务端用 TokenSession 私钥解密。
     */
    @Schema(description = "会话专属加密公钥（SPKI base64）")
    private String publicKey;
}
