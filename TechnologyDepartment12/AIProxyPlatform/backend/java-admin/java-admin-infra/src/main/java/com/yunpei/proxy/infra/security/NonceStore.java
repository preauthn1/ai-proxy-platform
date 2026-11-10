package com.yunpei.proxy.infra.security;

/**
 * Nonce 存储：在 TTL 内只允许同一 nonce 首次出现。
 *
 * @author yunpei
 */
public interface NonceStore {

    /**
     * 尝试占用 nonce。
     *
     * @param nonce nonce 值（通常为 X-Request-ID）
     * @param ttlMs 过期时间（毫秒）
     * @return {@code true} 首次占用成功；{@code false} 已存在（重放）
     */
    boolean tryAcquire(String nonce, long ttlMs);
}
