package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.api.dto.LoginRequest;
import com.yunpei.proxy.api.vo.LoginResponse;
import com.yunpei.proxy.api.vo.UserInfoVO;
import com.yunpei.proxy.common.exception.AuthException;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.auth.AuthService;
import com.yunpei.proxy.service.auth.LoginClientMeta;
import com.yunpei.proxy.service.auth.LoginResult;
import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.user.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

/**
 * {@link AuthController} 行为测试（standalone，不启 Spring 容器）。
 *
 * <p>验证登录成功响应含 accessToken + 会话 publicKey、失败透传 AuthException。
 *
 * @author yunpei
 */
class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final SysUserService sysUserService = mock(SysUserService.class);
    private final AuthController controller = new AuthController(authService, sysUserService);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void login_success_returnsAccessTokenAndSessionPublicKey() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("root");
        user.setNickname("Root");
        when(authService.login(
                        ArgumentMatchers.eq("root"),
                        ArgumentMatchers.eq("123456"),
                        ArgumentMatchers.eq("altcha-ok"),
                        ArgumentMatchers.any(LoginClientMeta.class)))
                .thenReturn(new LoginResult(user, List.of("root"), "/analytics"));
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        SaSession tokenSession = mock(SaSession.class);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.login(1L)).thenAnswer(inv -> null);
            stp.when(StpUtil::getTokenValue).thenReturn("token-abc");
            stp.when(StpUtil::getTokenSession).thenReturn(tokenSession);

            Result<LoginResponse> result = controller.login(loginReq("root", "123456", "altcha-ok"), request);

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData().getAccessToken()).isEqualTo("token-abc");
            assertThat(result.getData().getId()).isEqualTo(1L);
            assertThat(result.getData().getUsername()).isEqualTo("root");
            assertThat(result.getData().getRealName()).isEqualTo("Root");
            assertThat(result.getData().getRoles()).containsExactly("root");
            assertThat(result.getData().getHomePath()).isEqualTo("/analytics");
            assertThat(result.getData().getPublicKey()).isNotBlank();
            stp.verify(() -> StpUtil.login(1L));
            // 私钥与公钥均写入 TokenSession
            org.mockito.Mockito.verify(tokenSession)
                    .set(
                            org.mockito.ArgumentMatchers.eq("encryptPrivateKey"),
                            org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.verify(tokenSession)
                    .set(org.mockito.ArgumentMatchers.eq("encryptPublicKey"), org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    void login_invalidCredentials_propagatesAuthException() {
        when(authService.login(
                        ArgumentMatchers.eq("root"),
                        ArgumentMatchers.eq("bad"),
                        ArgumentMatchers.eq("altcha-ok"),
                        ArgumentMatchers.any(LoginClientMeta.class)))
                .thenThrow(AuthException.invalidCredentials());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        try {
            controller.login(loginReq("root", "bad", "altcha-ok"), request);
            throw new AssertionError("expected AuthException");
        } catch (AuthException ex) {
            assertThat(ex.getCode()).isEqualTo(2002);
        }
    }

    @Test
    void codes_returnsCodesFromService() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            when(authService.listAccessCodes(1L)).thenReturn(List.of("system:user:list", "system:role:list"));

            Result<List<String>> result = controller.codes();

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).containsExactly("system:user:list", "system:role:list");
        }
    }

    @Test
    void info_returnsUserInfo() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("root");
        user.setNickname("Root");
        user.setAvatar("");
        when(sysUserService.findById(1L)).thenReturn(user);
        when(authService.toUserSummary(user)).thenReturn(new LoginResult(user, List.of("root"), "/analytics"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            Result<UserInfoVO> result = controller.info();

            assertThat(result.getData().getUsername()).isEqualTo("root");
            assertThat(result.getData().getRealName()).isEqualTo("Root");
            assertThat(result.getData().getRoles()).containsExactly("root");
        }
    }

    @Test
    void logout_whenLogin_callsStpLogout() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::logout).thenAnswer(inv -> null);

            Result<Void> result = controller.logout();

            assertThat(result.getCode()).isEqualTo(0);
            stp.verify(StpUtil::logout);
        }
    }

    @Test
    void logout_whenNotLogin_stillOk() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);

            Result<Void> result = controller.logout();

            assertThat(result.getCode()).isEqualTo(0);
            stp.verify(StpUtil::logout, org.mockito.Mockito.never());
        }
    }

    private static LoginRequest loginReq(String username, String password, String altcha) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setAltcha(altcha);
        return req;
    }
}
