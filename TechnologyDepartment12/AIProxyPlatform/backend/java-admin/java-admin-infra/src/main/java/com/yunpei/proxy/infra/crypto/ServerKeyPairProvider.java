package com.yunpei.proxy.infra.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 全局服务器 RSA 密钥对提供者：Redis cache-aside + 进程内 volatile 缓存。
 *
 * @author yunpei
 */
@Slf4j
@Component
public final class ServerKeyPairProvider {

    private final EncryptKeyPairService service;

    private volatile String privateKeyPem;
    private volatile String publicKey;

    public ServerKeyPairProvider(EncryptKeyPairService service) {
        this.service = service;
    }

    public String getPrivateKeyPem() {
        if (privateKeyPem == null) {
            synchronized (this) {
                if (privateKeyPem == null) {
                    loadKeyPair();
                }
            }
        }
        return privateKeyPem;
    }

    public String getPublicKey() {
        if (publicKey == null) {
            synchronized (this) {
                if (publicKey == null) {
                    loadKeyPair();
                }
            }
        }
        return publicKey;
    }

    private void loadKeyPair() {
        EncryptKeyPair pair = service.getEncryptKeyPair();
        if (pair != null) {
            publicKey = pair.publicKey();
            privateKeyPem = pair.privateKey();
            log.debug("从 Redis 加载 encrypt key pair");
            return;
        }

        log.info("Redis 中无 encrypt key pair，生成新密钥对");
        EncryptKeyPairService.KeyPairResult result = service.generateAndCacheKeyPair();
        publicKey = result.publicKey();
        privateKeyPem = result.privateKeyPem();
    }
}
