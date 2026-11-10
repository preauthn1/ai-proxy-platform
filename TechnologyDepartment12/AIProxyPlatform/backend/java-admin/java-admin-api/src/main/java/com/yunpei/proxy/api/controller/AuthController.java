package com.yunpei.proxy.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.api.dto.LoginRequest;
import com.yunpei.proxy.api.vo.LoginResponse;
import com.yunpei.proxy.api.vo.UserInfoVO;
import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.request.RequestContext;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.util.ClientIpUtils;
import com.yunpei.proxy.infra.crypto.SessionEncryptKeys;
import com.yunpei.proxy.service.auth.AuthService;
import com.yunpei.proxy.service.auth.LoginClientMeta;
import com.yunpei.proxy.service.auth.LoginResult;
import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.user.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权 Controller（路径前缀 {@code /api/auth}，无 v1）。
 *
 * <p>login/logout 在 {@code WebConfig} SaInterceptor 白名单内；info/codes 由拦截器保证已登录，
 * 本类直接读取 loginId，不再重复 isLogin 门闩。logout 在未登录时幂等返回成功。
 *
 * @author yunpei
 */
@Slf4j
@Tag(name = "鉴权", description = "登录、登出、当前用户信息与权限码")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserService sysUserService;

    /**
     * 登录。
     */
    @PostMapping("/login")
    @Operation(
            summary = "账号密码 + ALTCHA 登录",
            description = "校验 ALTCHA 与凭证后签发 Sa-Token；返回 accessToken 与本次会话专属 publicKey（SPKI base64）")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "登录成功"),
                @ApiResponse(
                        responseCode = "400",
                        description = "参数错误(code=1001)",
                        content = @Content(schema = @Schema(implementation = Result.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "凭证错误(code=2002)",
                        content = @Content(schema = @Schema(implementation = Result.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "ALTCHA 失败或账号禁用(code=2004)",
                        content = @Content(schema = @Schema(implementation = Result.class)))
            })
    public Result<LoginResponse> login(
            @Parameter(description = "登录请求体", required = true) @Valid @RequestBody LoginRequest req,
            HttpServletRequest request) {
        LoginClientMeta meta = new LoginClientMeta(clientIp(request), userAgent(request));
        LoginResult result = authService.login(req.getUsername(), req.getPassword(), req.getAltcha(), meta);
        SysUser user = result.user();
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        // 对齐 Go PwdLogin：每 token 一对 RSA，私钥进 TokenSession，公钥回客户端
        SessionEncryptKeys.KeyPairStrings sessionKeys = SessionEncryptKeys.bindGeneratedKeyPairToCurrentToken();
        return Result.ok(new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                result.roles(),
                result.homePath(),
                sessionKeys.publicKey()));
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "注销当前 Sa-Token；未登录也返回成功（幂等）")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "登出成功（含未登录幂等）")})
    public Result<Void> logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return Result.ok();
    }

    /**
     * 当前用户信息。
     */
    @GetMapping("/info")
    @Operation(summary = "当前登录用户信息", description = "读取当前 Sa-Token 对应用户")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "成功"),
                @ApiResponse(
                        responseCode = "401",
                        description = "未登录(code=2001)",
                        content = @Content(schema = @Schema(implementation = Result.class)))
            })
    public Result<UserInfoVO> info() {
        // 登录态由 WebConfig SaInterceptor 保证
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "用户不存在");
        }
        LoginResult summary = authService.toUserSummary(user);
        return Result.ok(new UserInfoVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                summary.roles(),
                summary.homePath(),
                user.getAvatar()));
    }

    /**
     * 当前用户按钮权限码。
     */
    @GetMapping("/codes")
    @Operation(summary = "当前用户权限码", description = "BUTTON.permission_code 列表，供前端按钮鉴权")
    @SecurityRequirement(name = "bearerAuth")
    public Result<List<String>> codes() {
        // 登录态由 WebConfig SaInterceptor 保证
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(authService.listAccessCodes(userId));
    }

    /**
     * 优先用 Filter 预填的 {@link RequestContext#clientIpOrNull()}；standalone 单测等无 Context 时回退解析。
     */
    private static String clientIp(HttpServletRequest request) {
        String fromCtx = RequestContext.clientIpOrNull();
        if (fromCtx != null && !fromCtx.isBlank()) {
            return fromCtx;
        }
        return ClientIpUtils.resolve(
                request.getHeader(SecurityHeaders.FORWARDED_FOR),
                request.getHeader(SecurityHeaders.REAL_IP),
                request.getRemoteAddr(),
                request.getHeader(SecurityHeaders.PROXY_CLIENT_IP),
                request.getHeader(SecurityHeaders.WL_PROXY_CLIENT_IP));
    }

    private static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader(SecurityHeaders.USER_AGENT);
        return ua == null ? "" : ua;
    }
}
