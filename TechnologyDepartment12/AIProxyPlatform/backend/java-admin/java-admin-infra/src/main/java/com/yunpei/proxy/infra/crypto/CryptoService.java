package com.yunpei.proxy.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * 请求安全加解密：RSA-OAEP-SHA256 + AES-256-GCM，对齐 harness Go/Java 协议。
 *
 * @author yunpei
 */
public final class CryptoService {

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final OAEPParameterSpec RSA_OAEP_SHA256_PARAMS =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final int AES_KEY_SIZE = 32;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int GCM_TAG_BITS = 128;

    // PEM 标签拆开写，避免 gitleaks 把源码字符串误判为真实私钥
    private static final String PEM_PRIVATE_BEGIN = "-----BEGIN " + "PRIVATE KEY-----";
    private static final String PEM_PRIVATE_END = "-----END " + "PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_BEGIN = "-----BEGIN " + "RSA PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_END = "-----END " + "RSA PRIVATE KEY-----";
    private static final String PEM_PUBLIC_BEGIN = "-----BEGIN " + "PUBLIC KEY-----";
    private static final String PEM_PUBLIC_END = "-----END " + "PUBLIC KEY-----";

    // ---- RSA ----

    public String rsaDecrypt(String encryptedBase64, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, RSA_OAEP_SHA256_PARAMS);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("RSA decrypt failed", e);
        }
    }

    public String rsaEncrypt(String plainText, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, RSA_OAEP_SHA256_PARAMS);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new CryptoException("RSA encrypt failed", e);
        }
    }

    // ---- AES-GCM Encrypt ----

    /**
     * AES-256-GCM 加密。
     *
     * @return combined = base64(ciphertext + tag + iv)
     */
    public EncryptResult aesEncrypt(String plainText, String aesKeyBase64, String aad) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
            if (keyBytes.length != AES_KEY_SIZE) {
                throw new CryptoException("AES key must be 256 bits");
            }

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }

            byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] ciphertext = new byte[sealed.length - GCM_TAG_LENGTH];
            byte[] tag = new byte[GCM_TAG_LENGTH];
            System.arraycopy(sealed, 0, ciphertext, 0, ciphertext.length);
            System.arraycopy(sealed, ciphertext.length, tag, 0, GCM_TAG_LENGTH);

            byte[] tagIv = new byte[GCM_TAG_LENGTH + GCM_IV_LENGTH];
            System.arraycopy(tag, 0, tagIv, 0, GCM_TAG_LENGTH);
            System.arraycopy(iv, 0, tagIv, GCM_TAG_LENGTH, GCM_IV_LENGTH);

            byte[] combined = new byte[sealed.length + GCM_IV_LENGTH];
            System.arraycopy(sealed, 0, combined, 0, sealed.length);
            System.arraycopy(iv, 0, combined, sealed.length, GCM_IV_LENGTH);

            return new EncryptResult(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(tagIv),
                    Base64.getEncoder().encodeToString(combined));
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("AES encrypt failed", e);
        }
    }

    // ---- AES-GCM Decrypt ----

    /** 解密 combined 格式：base64(ciphertext + tag + iv)。 */
    public byte[] aesDecryptCombined(String combinedBase64, String aesKeyBase64, String aad) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
            if (keyBytes.length != AES_KEY_SIZE) {
                throw new CryptoException("AES key must be 256 bits");
            }

            byte[] data = Base64.getDecoder().decode(combinedBase64);
            if (data.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                throw new CryptoException("Combined data too short");
            }

            byte[] sealed = new byte[data.length - GCM_IV_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, sealed, 0, sealed.length);
            System.arraycopy(data, sealed.length, iv, 0, GCM_IV_LENGTH);

            return gcmDecrypt(sealed, iv, keyBytes, aad);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("AES decrypt failed", e);
        }
    }

    /** 解密分离的 ciphertext + tagIv（tag+iv）。 */
    public byte[] aesDecryptCiphertextAndTag(
            String ciphertextBase64, String tagIvBase64, String aesKeyBase64, String aad) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
            if (keyBytes.length != AES_KEY_SIZE) {
                throw new CryptoException("AES key must be 256 bits");
            }

            byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertextBase64);
            byte[] tagIvBytes = Base64.getDecoder().decode(tagIvBase64);

            if (tagIvBytes.length != GCM_TAG_LENGTH + GCM_IV_LENGTH) {
                throw new CryptoException("tagIv must be " + (GCM_TAG_LENGTH + GCM_IV_LENGTH) + " bytes");
            }

            byte[] tag = new byte[GCM_TAG_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(tagIvBytes, 0, tag, 0, GCM_TAG_LENGTH);
            System.arraycopy(tagIvBytes, GCM_TAG_LENGTH, iv, 0, GCM_IV_LENGTH);

            byte[] sealed = new byte[ciphertextBytes.length + GCM_TAG_LENGTH];
            System.arraycopy(ciphertextBytes, 0, sealed, 0, ciphertextBytes.length);
            System.arraycopy(tag, 0, sealed, ciphertextBytes.length, GCM_TAG_LENGTH);

            return gcmDecrypt(sealed, iv, keyBytes, aad);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("AES decrypt failed", e);
        }
    }

    private byte[] gcmDecrypt(byte[] sealed, byte[] iv, byte[] keyBytes, String aad) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        if (aad != null && !aad.isEmpty()) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }

        return cipher.doFinal(sealed);
    }

    // ---- AAD ----

    /**
     * 构建 AAD：按 key 排序后 key=value 用 & 连接；空值跳过。
     */
    public String buildAad(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String value = e.getValue();
            if (value != null && !value.isEmpty()) {
                sorted.put(e.getKey(), value);
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    /** 用 AES-GCM 作为 MAC 校验签名（空 ciphertext + tagIv）。 */
    public boolean verifySign(String signBase64, String aesKeyBase64, String aad) {
        try {
            aesDecryptCiphertextAndTag("", signBase64, aesKeyBase64, aad);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Key generation ----

    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new CryptoException("RSA key generation failed", e);
        }
    }

    public static String generateAesKey() {
        byte[] key = new byte[AES_KEY_SIZE];
        try {
            SecureRandom.getInstanceStrong().nextBytes(key);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("Failed to generate AES key", e);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    // ---- PEM helpers ----

    public static PrivateKey parsePrivateKeyPem(String pem) {
        try {
            String b64 = pem.replace(PEM_PRIVATE_BEGIN, "")
                    .replace(PEM_PRIVATE_END, "")
                    .replace(PEM_RSA_PRIVATE_BEGIN, "")
                    .replace(PEM_RSA_PRIVATE_END, "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            try {
                return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (Exception e) {
                return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs1ToPkcs8(der)));
            }
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to parse private key PEM", e);
        }
    }

    public static PublicKey parsePublicKeyPem(String pemOrBase64) {
        try {
            String b64 = pemOrBase64
                    .replace(PEM_PUBLIC_BEGIN, "")
                    .replace(PEM_PUBLIC_END, "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new CryptoException("Failed to parse public key PEM", e);
        }
    }

    private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        try {
            byte[] rsaOid = new byte[] {
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00
            };
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            out.write(0x02);
            out.write(1);
            out.write(0x00);
            out.write(0x30);
            out.write(rsaOid.length);
            out.write(rsaOid);
            out.write(0x04);
            writeLength(out, pkcs1.length);
            out.write(pkcs1);
            byte[] inner = out.toByteArray();
            java.io.ByteArrayOutputStream pkcs8 = new java.io.ByteArrayOutputStream();
            pkcs8.write(0x30);
            writeLength(pkcs8, inner.length);
            pkcs8.write(inner);
            return pkcs8.toByteArray();
        } catch (Exception e) {
            throw new CryptoException("PKCS1 to PKCS8 conversion failed", e);
        }
    }

    private static void writeLength(java.io.ByteArrayOutputStream out, int len) {
        if (len < 128) {
            out.write(len);
        } else if (len < 256) {
            out.write(0x81);
            out.write(len);
        } else {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len & 0xFF);
        }
    }

    public static String toPem(PublicKey publicKey) {
        String b64 = toBase64(publicKey);
        return PEM_PUBLIC_BEGIN + "\n"
                + b64.replaceAll("(.{64})", "$1\n")
                + (b64.length() % 64 == 0 ? "" : "\n")
                + PEM_PUBLIC_END + "\n";
    }

    public static String toPem(PrivateKey privateKey) {
        String b64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return PEM_PRIVATE_BEGIN + "\n"
                + b64.replaceAll("(.{64})", "$1\n")
                + (b64.length() % 64 == 0 ? "" : "\n")
                + PEM_PRIVATE_END + "\n";
    }

    public static String toBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** AES-GCM 加密结果：分片密文、tag+iv、以及合并 payload。 */
    public record EncryptResult(String ciphertext, String tagIv, String combined) {}

    public static final class CryptoException extends RuntimeException {
        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
