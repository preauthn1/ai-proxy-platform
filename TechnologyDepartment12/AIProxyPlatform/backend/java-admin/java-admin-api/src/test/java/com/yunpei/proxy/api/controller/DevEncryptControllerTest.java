package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.api.vo.RsaKeyPairVO;
import com.yunpei.proxy.common.exception.AuthException;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.infra.crypto.ServerKeyPairProvider;
import com.yunpei.proxy.infra.crypto.SessionEncryptKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link DevEncryptController} 单元测试（dev-only key-pair / session-key 形状）。
 *
 * @author yunpei
 */
class DevEncryptControllerTest {

    @Test
    void keyPair_returnsPublicAndPrivateKeys() {
        ServerKeyPairProvider provider = mock(ServerKeyPairProvider.class);
        when(provider.getPublicKey()).thenReturn("BASE64_PUBLIC_KEY");
        when(provider.getPrivateKeyPem()).thenReturn("REDACTED");
        DevEncryptController controller = new DevEncryptController(provider);

        Result<RsaKeyPairVO> result = controller.keyPair();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getPublicKey()).isEqualTo("BASE64_PUBLIC_KEY");
        assertThat(result.getData().getPrivateKey()).contains("BEGIN PRIVATE KEY");
    }

    @Test
    void sessionKey_withBearer_returnsSessionKeyPair() {
        ServerKeyPairProvider provider = mock(ServerKeyPairProvider.class);
        DevEncryptController controller = new DevEncryptController(provider);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok-1");

        SaSession tokenSession = mock(SaSession.class);
        when(tokenSession.get(SessionEncryptKeys.SESSION_PRIVATE_KEY))
                .thenReturn("REDACTED");
        when(tokenSession.get(SessionEncryptKeys.SESSION_PUBLIC_KEY)).thenReturn("SESSION_PUBLIC");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getTokenSessionByToken("tok-1")).thenReturn(tokenSession);

            Result<RsaKeyPairVO> result = controller.sessionKey(request);

            assertThat(result.getCode()).isZero();
            assertThat(result.getData().getPublicKey()).isEqualTo("SESSION_PUBLIC");
            assertThat(result.getData().getPrivateKey())
                    .isEqualTo("REDACTED");
        }
    }

    @Test
    void sessionKey_withoutToken_throwsNotLogin() {
        DevEncryptController controller = new DevEncryptController(mock(ServerKeyPairProvider.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThatThrownBy(() -> controller.sessionKey(request)).isInstanceOf(AuthException.class);
    }
}
