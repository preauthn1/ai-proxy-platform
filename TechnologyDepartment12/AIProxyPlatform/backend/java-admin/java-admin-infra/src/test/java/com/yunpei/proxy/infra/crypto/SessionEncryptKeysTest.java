package com.yunpei.proxy.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link SessionEncryptKeys} 解析与提取逻辑。
 *
 * @author yunpei
 */
class SessionEncryptKeysTest {

    @Test
    void extractBearerToken_stripsPrefix() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer  abc-token ");
        assertThat(SessionEncryptKeys.extractBearerToken(request)).isEqualTo("abc-token");
    }

    @Test
    void resolvePrivateKeyPem_prefersSessionOverGlobal() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok-x");
        ServerKeyPairProvider global = mock(ServerKeyPairProvider.class);
        when(global.getPrivateKeyPem()).thenReturn("GLOBAL_PEM");

        SaSession tokenSession = mock(SaSession.class);
        when(tokenSession.get(SessionEncryptKeys.SESSION_PRIVATE_KEY)).thenReturn("SESSION_PEM");
        when(tokenSession.get(SessionEncryptKeys.SESSION_PUBLIC_KEY)).thenReturn("PUB");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getTokenSessionByToken("tok-x")).thenReturn(tokenSession);

            assertThat(SessionEncryptKeys.resolvePrivateKeyPem(request, global)).isEqualTo("SESSION_PEM");
        }
    }

    @Test
    void resolvePrivateKeyPem_fallsBackToGlobalWhenNoSessionKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        ServerKeyPairProvider global = mock(ServerKeyPairProvider.class);
        when(global.getPrivateKeyPem()).thenReturn("GLOBAL_PEM");

        assertThat(SessionEncryptKeys.resolvePrivateKeyPem(request, global)).isEqualTo("GLOBAL_PEM");
    }
}
