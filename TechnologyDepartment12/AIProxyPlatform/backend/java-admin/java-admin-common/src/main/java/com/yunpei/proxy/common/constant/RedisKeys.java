package com.yunpei.proxy.common.constant;

/**
 * Redis Key 前缀与命名空间。
 *
 * @author yunpei
 */
public final class RedisKeys {

    /** Sa-Token 业务 key 前缀（sa-token-redis-template 内部使用） */
    public static final String SA_TOKEN_PREFIX = "satoken:";

    /**
     * 全局 RSA 加解密密钥对（JSON：publicKey + privateKey）。
     * key 名沿用 harness/Go 约定 {@code global:encrypt:public:key}，实际存完整密钥对。
     */
    public static final String GLOBAL_ENCRYPT_KEY_PAIR = "global:encrypt:public:key";

    /**
     * Nonce 防重放 key 前缀；完整 key = 前缀 + {@code X-Request-ID}。
     * 对齐 Go {@code security:nonce:%s}。
     */
    public static final String SECURITY_NONCE_PREFIX = "security:nonce:";

    private RedisKeys() {}
}
