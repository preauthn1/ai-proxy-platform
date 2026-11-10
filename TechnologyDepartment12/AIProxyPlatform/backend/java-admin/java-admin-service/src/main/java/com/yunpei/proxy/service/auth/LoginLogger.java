package com.yunpei.proxy.service.auth;

import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.common.util.UserAgentParser;
import com.yunpei.proxy.service.entity.SysLoginLog;
import com.yunpei.proxy.service.repository.SysLoginLogRepository;
import com.yunpei.proxy.service.support.geo.IpLocationResolver;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 密码登录日志写入（对齐 Go {@code appsvc.LoginLogger / login_logger_impl}）。
 *
 * <p>异步写库：IP 归属地查询 + insert 不阻塞登录主流程；失败仅打 error 日志。
 *
 * @author yunpei
 */
@Slf4j
@Service
public class LoginLogger {

    /** 与 Go admin Web 端一致的客户端 ID 占位；可后续按多端扩展。 */
    public static final String DEFAULT_CLIENT_ID = "web-admin";

    private final SysLoginLogRepository sysLoginLogRepository;
    private final IpLocationResolver ipLocationResolver;
    private final Executor loginLogExecutor;

    public LoginLogger(
            SysLoginLogRepository sysLoginLogRepository,
            IpLocationResolver ipLocationResolver,
            @Qualifier("loginLogExecutor") Executor loginLogExecutor) {
        this.sysLoginLogRepository = sysLoginLogRepository;
        this.ipLocationResolver = ipLocationResolver;
        this.loginLogExecutor = loginLogExecutor;
    }

    /**
     * 记录一次密码登录尝试（成功或失败）。
     *
     * @param username   登录用户名
     * @param userId     关联用户 ID（未知时为 null）
     * @param statusCode HTTP 风格状态码
     * @param success    是否成功
     * @param reason     失败原因（成功时建议空串）
     * @param clientMeta 客户端 IP / UA
     */
    public void recordPwdLogin(
            String username, Long userId, int statusCode, boolean success, String reason, LoginClientMeta clientMeta) {
        LoginClientMeta meta = clientMeta == null ? LoginClientMeta.empty() : clientMeta;
        String safeUsername = username == null ? "" : username;
        String safeReason = reason == null ? "" : reason;
        LocalDateTime loginTime = TimeZones.now();

        loginLogExecutor.execute(
                () -> insertQuietly(safeUsername, userId, statusCode, success, safeReason, meta, loginTime));
    }

    private void insertQuietly(
            String username,
            Long userId,
            int statusCode,
            boolean success,
            String reason,
            LoginClientMeta meta,
            LocalDateTime loginTime) {
        try {
            String userAgent = meta.userAgent();
            UserAgentParser.Parsed ua = UserAgentParser.parse(userAgent);
            String location = ipLocationResolver.resolve(meta.loginIp());
            String clientName = ua.clientName();
            if (clientName.isBlank()) {
                clientName = "Web Admin";
            }

            SysLoginLog row = new SysLoginLog();
            row.setUsername(username);
            row.setSuccess(success ? 1 : 0);
            row.setReason(reason);
            row.setStatusCode(statusCode);
            row.setSysUserId(userId);
            row.setLoginMethod("PASSWORD");
            row.setLoginTime(loginTime);
            row.setLoginIp(meta.loginIp());
            row.setLoginMac(""); // Web 场景通常不可得
            row.setClientId(DEFAULT_CLIENT_ID);
            row.setClientName(clientName);
            row.setUserAgent(userAgent);
            row.setBrowserName(ua.browserName());
            row.setBrowserVersion(ua.browserVersion());
            row.setOsName(ua.osName());
            row.setOsVersion(ua.osVersion());
            row.setLocation(location);
            row.setCreatedAt(loginTime);
            sysLoginLogRepository.insert(row);
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("logType", "AUTH")
                    .addKeyValue("username", username)
                    .setCause(e)
                    .log("write login log failed");
        }
    }
}
