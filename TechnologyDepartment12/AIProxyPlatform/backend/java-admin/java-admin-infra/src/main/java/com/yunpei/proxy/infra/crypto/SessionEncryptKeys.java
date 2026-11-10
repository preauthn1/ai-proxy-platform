package com.yunpei.proxy.infra.crypto;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.common.constant.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.security.KeyPair;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录会话专属 RSA 密钥：按 Sa-Token <strong>TokenSession</strong> 绑定，多端互不覆盖。
 *
 * <p>对齐 harness Go：登录生成密钥对、会话存私钥、响应返回公钥；Filter 解密优先会话私钥，否则全局钥。
 *
 * @author yunpei
 */
@Slf4j
public final class SessionEncryptKeys {

    /** TokenSession 中私钥 PEM 字段 */
    public static final String SESSION_PRIVATE_KEY = "encryptPrivateKey";

    /** TokenSession 中公钥 SPKI base64 字段 */
    public static final String SESSION_PUBLIC_KEY = "encryptPublicKey";

    private SessionEncryptKeys() {}

    /**
     * 为当前登录 token 生成 RSA 密钥对并写入 TokenSession。
     *
     * <p>须在 {@link StpUtil#login} 之后调用。
     */
    public static KeyPairStrings bindGeneratedKeyPairToCurrentToken() {
        KeyPair keyPair = CryptoService.generateRsaKeyPair();
        String publicKey = CryptoService.toBase64(keyPair.getPublic());
        String privateKeyPem = CryptoService.toPem(keyPair.getPrivate());
        SaSession tokenSession = StpUtil.getTokenSession();
        tokenSession.set(SESSION_PRIVATE_KEY, privateKeyPem);
        tokenSession.set(SESSION_PUBLIC_KEY, publicKey);
        return new KeyPairStrings(publicKey, privateKeyPem);
    }

    /**
     * 解析解密用私钥：请求带有效 Bearer 且 TokenSession 有会话钥 → 用会话私钥；否则全局钥。
     */
    public static String resolvePrivateKeyPem(HttpServletRequest request, ServerKeyPairProvider global) {
        String token = extractBearerToken(request);
        if (token != null) {
            String sessionPem = findPrivateKeyPemByToken(token);
            if (sessionPem != null) {
                return sessionPem;
            }
        }
        return global.getPrivateKeyPem();
    }

    /** 按 token 读取会话密钥对；无会话或字段缺失返回 {@code null}。 */
    public static KeyPairStrings findKeyPairByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token.trim());
            if (tokenSession == null) {
                return null;
            }
            Object privateObj = tokenSession.get(SESSION_PRIVATE_KEY);
            Object publicObj = tokenSession.get(SESSION_PUBLIC_KEY);
            if (!(privateObj instanceof String privateKey) || privateKey.isBlank()) {
                return null;
            }
            String publicKey = publicObj instanceof String pub && !pub.isBlank() ? pub : "";
            return new KeyPairStrings(publicKey, privateKey);
        } catch (Exception e) {
            log.atDebug().addKeyValue("msg", e.getMessage()).log("读取 token 会话加密密钥失败");
            return null;
        }
    }

    public static String findPrivateKeyPemByToken(String token) {
        KeyPairStrings pair = findKeyPairByToken(token);
        return pair == null ? null : pair.privateKeyPem();
    }

    /**
     * 从 Authorization: Bearer &lt;token&gt; 提取 token 值（去掉 Bearer 前缀）。
     */
    public static String extractBearerToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(SecurityConstants.TOKEN_NAME);
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value.isEmpty() ? null : value;
    }

    /** 公钥 SPKI base64 + 私钥 PKCS#8 PEM。 */
    public record KeyPairStrings(String publicKey, String privateKeyPem) {}
}
