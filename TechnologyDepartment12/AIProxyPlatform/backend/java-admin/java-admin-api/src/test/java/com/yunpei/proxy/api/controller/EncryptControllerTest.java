package com.yunpei.proxy.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.api.vo.PublicKeyVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.infra.crypto.ServerKeyPairProvider;
import org.junit.jupiter.api.Test;

/**
 * {@link EncryptController} 单元测试。
 *
 * @author yunpei
 */
class EncryptControllerTest {

    @Test
    void publicKey_returnsResultWithPublicKeyField() {
        ServerKeyPairProvider provider = mock(ServerKeyPairProvider.class);
        when(provider.getPublicKey()).thenReturn("BASE64_PUBLIC_KEY");
        EncryptController controller = new EncryptController(provider);

        Result<PublicKeyVO> result = controller.publicKey();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getPublicKey()).isEqualTo("BASE64_PUBLIC_KEY");
    }
}
