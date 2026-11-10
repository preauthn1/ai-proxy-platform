package com.yunpei.proxy.infra.crypto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全加解密相关 Bean。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public final class SecurityCryptoConfig {

    @Bean
    public CryptoService cryptoService() {
        return new CryptoService();
    }
}
