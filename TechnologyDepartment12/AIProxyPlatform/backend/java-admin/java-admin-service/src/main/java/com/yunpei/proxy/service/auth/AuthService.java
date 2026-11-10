package com.yunpei.proxy.service.auth;

import com.yunpei.proxy.common.exception.AuthException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.blacklist.BlacklistService;
import com.yunpei.proxy.service.blacklist.BlacklistService.BlacklistHit;
import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.repository.AuthQueryRepository;
import com.yunpei.proxy.service.repository.SysUserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权 Service。
 *
 * <p>登录：ALTCHA → 用户名密码（BCrypt）→ USER 黑名单（scope 覆盖 LOGIN）→ 写登录日志 → 返回用户与角色摘要。
 * Sa-Token 登录态由 Controller 写入（本类在返回前完成 USER 拦截，保证发 token 前拒绝）。
 * 登录日志经 {@link LoginLogger} 异步落库。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_HOME_PATH = "/analytics";

    /** 默认 cost=10；兼容校验 jBCrypt 生成的 $2a$ 哈希（见 V2__schema_seed.sql）。 */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final SysUserRepository sysUserRepository;
    private final AuthQueryRepository authQueryRepository;
    private final AltchaService altchaService;
    private final LoginLogger loginLogger;
    private final BlacklistService blacklistService;

    /**
     * 登录校验（含 ALTCHA 与登录日志）。
     *
     * @param username   用户名
     * @param password   明文密码
     * @param altcha     ALTCHA Base64 payload
     * @param clientMeta 客户端 IP / UA
     * @return 登录业务结果
     * @throws AuthException 校验失败
     */
    public LoginResult login(String username, String password, String altcha, LoginClientMeta clientMeta) {
        LoginClientMeta meta = clientMeta == null ? LoginClientMeta.empty() : clientMeta;
        String safeUsername = username == null ? "" : username.trim();

        verifyAltcha(altcha, safeUsername, meta);
        requireCredentials(safeUsername, password, meta);
        SysUser user = requireActiveUser(safeUsername, meta);
        verifyPassword(password, user, safeUsername, meta);
        rejectIfUserBlacklisted(user, safeUsername, meta);

        List<String> roles = authQueryRepository.findRoleCodesByUserId(user.getId());
        loginLogger.recordPwdLogin(safeUsername, user.getId(), 200, true, "", meta);
        log.atInfo()
                .addKeyValue("logType", "AUTH")
                .addKeyValue("userId", user.getId())
                .addKeyValue("username", safeUsername)
                .log("login success");
        return new LoginResult(user, roles, DEFAULT_HOME_PATH);
    }

    /**
     * 当前用户的按钮权限码列表。
     */
    public List<String> listAccessCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return authQueryRepository.findAccessCodesByUserId(userId);
    }

    /**
     * 当前用户角色编码列表。
     */
    public List<String> listRoleCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return authQueryRepository.findRoleCodesByUserId(userId);
    }

    /**
     * 组装对外用户摘要（info / 登录响应共用字段）。
     */
    public LoginResult toUserSummary(SysUser user) {
        List<String> roles = listRoleCodes(user.getId());
        return new LoginResult(user, roles, DEFAULT_HOME_PATH);
    }

    private void verifyAltcha(String altcha, String username, LoginClientMeta meta) {
        if (!altchaService.verify(altcha)) {
            loginLogger.recordPwdLogin(username, null, 403, false, "ALTCHA verification failed", meta);
            throw new AuthException(ResultCode.AUTH_FORBIDDEN, "ALTCHA 校验失败");
        }
    }

    private void requireCredentials(String username, String password, LoginClientMeta meta) {
        if (username.isBlank() || password == null || password.isBlank()) {
            loginLogger.recordPwdLogin(username, null, 400, false, "Username and password are required", meta);
            throw AuthException.invalidCredentials();
        }
    }

    private SysUser requireActiveUser(String username, LoginClientMeta meta) {
        SysUser user = sysUserRepository.findByUsername(username);
        if (user == null) {
            log.atWarn()
                    .addKeyValue("username", username)
                    .addKeyValue("logType", "AUTH")
                    .addKeyValue("reason", "USER_NOT_FOUND")
                    .log("login failed");
            loginLogger.recordPwdLogin(username, null, 401, false, "Username or password is incorrect", meta);
            throw AuthException.invalidCredentials();
        }
        if (user.getIsEnabled() == null || user.getIsEnabled() != 1) {
            log.atWarn()
                    .addKeyValue("username", username)
                    .addKeyValue("logType", "AUTH")
                    .addKeyValue("reason", "USER_DISABLED")
                    .log("login failed");
            loginLogger.recordPwdLogin(username, user.getId(), 403, false, "User disabled", meta);
            throw new AuthException(ResultCode.AUTH_FORBIDDEN, "账号已禁用");
        }
        return user;
    }

    private void verifyPassword(String password, SysUser user, String username, LoginClientMeta meta) {
        if (!PASSWORD_ENCODER.matches(password, user.getPasswordHash())) {
            log.atWarn()
                    .addKeyValue("username", username)
                    .addKeyValue("logType", "AUTH")
                    .addKeyValue("reason", "BAD_PASSWORD")
                    .log("login failed");
            loginLogger.recordPwdLogin(username, user.getId(), 401, false, "Username or password is incorrect", meta);
            throw AuthException.invalidCredentials();
        }
    }

    /**
     * 用户解析成功且密码正确后、发 token 前：查 USER 黑名单（scope 覆盖 LOGIN）。
     *
     * <p>Filter 不解析登录 body，故 USER 拦截落在本链路；IP 由 BlacklistFilter 在 LOGIN 场景处理。
     */
    private void rejectIfUserBlacklisted(SysUser user, String username, LoginClientMeta meta) {
        Optional<BlacklistHit> hit =
                blacklistService.findBlockingHit("USER", String.valueOf(user.getId()), "LOGIN", null);
        if (hit.isEmpty()) {
            return;
        }
        BlacklistHit h = hit.get();
        log.atWarn()
                .addKeyValue("logType", "AUTH")
                .addKeyValue("username", username)
                .addKeyValue("userId", user.getId())
                .addKeyValue("blacklistScope", h.scope())
                .addKeyValue("reason", h.reason())
                .log("login Access Blocked");
        loginLogger.recordPwdLogin(username, user.getId(), 403, false, "Access blocked", meta);
        throw AuthException.accessBlocked();
    }
}
