package com.yunpei.proxy.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.yunpei.proxy.service.entity.SysLoginLog;
import com.yunpei.proxy.service.repository.SysLoginLogRepository;
import com.yunpei.proxy.service.support.geo.IpLocationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LoginLogger} 单元测试（同步 Executor，断言字段填充）。
 *
 * @author yunpei
 */
@ExtendWith(MockitoExtension.class)
class LoginLoggerTest {

    private static final String CHROME_WINDOWS_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Mock
    private SysLoginLogRepository sysLoginLogRepository;

    private LoginLogger loginLogger;

    @BeforeEach
    void initLoginLogger() {
        // 无 xdb 注入 null searcher：本机/内网仍可用
        IpLocationResolver resolver = new IpLocationResolver(null, null);
        loginLogger = new LoginLogger(sysLoginLogRepository, resolver, Runnable::run);
    }

    @Test
    void recordPwdLogin_success_fillsUaOsLocationAndClientName() {
        LoginClientMeta meta = new LoginClientMeta("127.0.0.1", CHROME_WINDOWS_UA);

        loginLogger.recordPwdLogin("root", 1L, 200, true, "", meta);

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(sysLoginLogRepository).insert(captor.capture());
        SysLoginLog row = captor.getValue();
        assertThat(row.getUsername()).isEqualTo("root");
        assertThat(row.getSuccess()).isEqualTo(1);
        assertThat(row.getStatusCode()).isEqualTo(200);
        assertThat(row.getSysUserId()).isEqualTo(1L);
        assertThat(row.getLoginIp()).isEqualTo("127.0.0.1");
        assertThat(row.getUserAgent()).isEqualTo(CHROME_WINDOWS_UA);
        assertThat(row.getBrowserName()).isEqualTo("Chrome");
        assertThat(row.getBrowserVersion()).startsWith("122.");
        assertThat(row.getOsName()).isEqualTo("Windows");
        assertThat(row.getOsVersion()).isEqualTo("10/11");
        assertThat(row.getLocation()).isEqualTo("本机");
        assertThat(row.getClientId()).isEqualTo(LoginLogger.DEFAULT_CLIENT_ID);
        assertThat(row.getClientName()).isEqualTo("PC");
        assertThat(row.getLoginMethod()).isEqualTo("PASSWORD");
    }

    @Test
    void recordPwdLogin_failure_writesReason() {
        LoginClientMeta meta = new LoginClientMeta("192.168.1.1", "CustomBot/1.0");

        loginLogger.recordPwdLogin("root", null, 403, false, "ALTCHA verification failed", meta);

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(sysLoginLogRepository).insert(captor.capture());
        SysLoginLog row = captor.getValue();
        assertThat(row.getSuccess()).isEqualTo(0);
        assertThat(row.getStatusCode()).isEqualTo(403);
        assertThat(row.getReason()).contains("ALTCHA");
        assertThat(row.getLocation()).isEqualTo("内网");
        assertThat(row.getBrowserName()).isEqualTo("Unknown");
    }
}
