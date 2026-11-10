package com.yunpei.proxy.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link CryptoService} 加解密协议测试。
 *
 * @author yunpei
 */
class CryptoServiceTest {

    private static CryptoService service;
    private static String aesKeyBase64;
    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    private static String privateKeyPem;
    private static String publicKeyBase64;

    private static final String PLAIN_BODY = "{\"username\":\"admin\"}";

    @BeforeAll
    static void initKeys() {
        service = new CryptoService();
        KeyPair rsaKeyPair = CryptoService.generateRsaKeyPair();
        privateKey = rsaKeyPair.getPrivate();
        publicKey = rsaKeyPair.getPublic();
        privateKeyPem = CryptoService.toPem(privateKey);
        publicKeyBase64 = CryptoService.toBase64(publicKey);
        aesKeyBase64 = CryptoService.generateAesKey();
    }

    @Test
    void rsa_roundTrip_preservesAesKey() {
        String encrypted = service.rsaEncrypt(aesKeyBase64, publicKey);
        String decrypted = service.rsaDecrypt(encrypted, privateKey);
        assertThat(decrypted).isEqualTo(aesKeyBase64);
    }

    @Test
    void rsa_viaPemAndBase64PublicKey_works() {
        PrivateKey parsedPriv = CryptoService.parsePrivateKeyPem(privateKeyPem);
        PublicKey parsedPub = CryptoService.parsePublicKeyPem(publicKeyBase64);

        String encrypted = service.rsaEncrypt(aesKeyBase64, parsedPub);
        String decrypted = service.rsaDecrypt(encrypted, parsedPriv);
        assertThat(decrypted).isEqualTo(aesKeyBase64);
    }

    @Test
    void rsa_wrongKey_throws() {
        KeyPair other = CryptoService.generateRsaKeyPair();
        String encrypted = service.rsaEncrypt(aesKeyBase64, other.getPublic());
        assertThatThrownBy(() -> service.rsaDecrypt(encrypted, privateKey))
                .isInstanceOf(CryptoService.CryptoException.class);
    }

    @Test
    void aes_combined_roundTripWithAad() {
        String aad = buildAad();
        CryptoService.EncryptResult result = service.aesEncrypt(PLAIN_BODY, aesKeyBase64, aad);
        byte[] plain = service.aesDecryptCombined(result.combined(), aesKeyBase64, aad);
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo(PLAIN_BODY);
    }

    @Test
    void aes_ciphertextAndTag_roundTrip() {
        String aad = buildAad();
        CryptoService.EncryptResult result = service.aesEncrypt(PLAIN_BODY, aesKeyBase64, aad);
        byte[] plain = service.aesDecryptCiphertextAndTag(result.ciphertext(), result.tagIv(), aesKeyBase64, aad);
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo(PLAIN_BODY);
    }

    @Test
    void aes_wrongAad_fails() {
        CryptoService.EncryptResult result = service.aesEncrypt(PLAIN_BODY, aesKeyBase64, buildAad());
        assertThatThrownBy(() -> service.aesDecryptCombined(result.combined(), aesKeyBase64, "bad=aad"))
                .isInstanceOf(CryptoService.CryptoException.class);
    }

    @Test
    void buildAad_sortsKeysAndSkipsEmpty() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("c", "");
        assertThat(service.buildAad(params)).isEqualTo("a=1&b=2");
    }

    private static String buildAad() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("X-Request-ID", "test-request-id-0001");
        params.put("X-Request-Timestamp", "1711411200000");
        return service.buildAad(params);
    }
}
