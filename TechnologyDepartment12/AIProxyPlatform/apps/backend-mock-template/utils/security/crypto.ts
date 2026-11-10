/**
 * 请求安全加解密：RSA-OAEP-SHA256 + AES-256-GCM，与 Java CryptoService 协议一致。
 *
 * - 请求 body：ciphertext base64 放 body；tag+iv base64 放 X-Request-Signature
 * - 响应 body：combined = base64(ciphertext + tag + iv)
 * - AAD：参数按 key 排序后 key=value 用 & 连接，空值跳过
 */

import {
  constants,
  createCipheriv,
  createDecipheriv,
  createPrivateKey,
  createPublicKey,
  generateKeyPairSync,
  privateDecrypt,
  publicEncrypt,
  randomBytes,
  type KeyObject,
} from "node:crypto";

const AES_KEY_SIZE = 32;
const GCM_IV_LENGTH = 12;
const GCM_TAG_LENGTH = 16;

export class CryptoError extends Error {
  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = "CryptoError";
    if (cause !== undefined) {
      (this as Error & { cause?: unknown }).cause = cause;
    }
  }
}

export interface EncryptResult {
  ciphertext: string;
  tagIv: string;
  combined: string;
}

export interface RsaKeyPairPem {
  /** X.509 SPKI base64（无 PEM 头，与 Java publicKey 字段一致） */
  publicKeyBase64: string;
  /** PKCS#8 PEM */
  privateKeyPem: string;
}

// ---- RSA ----

export function generateRsaKeyPair(): RsaKeyPairPem {
  const pair = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "der" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  return {
    publicKeyBase64: Buffer.from(pair.publicKey).toString("base64"),
    privateKeyPem: pair.privateKey,
  };
}

export function generateAesKey(): string {
  return randomBytes(AES_KEY_SIZE).toString("base64");
}

export function parsePublicKey(pemOrBase64: string): KeyObject {
  const trimmed = pemOrBase64.trim();
  if (trimmed.includes("BEGIN")) {
    return createPublicKey(trimmed);
  }
  const der = Buffer.from(trimmed.replace(/\s/g, ""), "base64");
  return createPublicKey({ key: der, format: "der", type: "spki" });
}

export function parsePrivateKeyPem(pem: string): KeyObject {
  return createPrivateKey(pem);
}

export function rsaEncrypt(plainText: string, publicKey: KeyObject | string): string {
  const key = typeof publicKey === "string" ? parsePublicKey(publicKey) : publicKey;
  try {
    const encrypted = publicEncrypt(
      {
        key,
        padding: constants.RSA_PKCS1_OAEP_PADDING,
        oaepHash: "sha256",
      },
      Buffer.from(plainText, "utf8"),
    );
    return encrypted.toString("base64");
  } catch (e) {
    throw new CryptoError("RSA encrypt failed", e);
  }
}

export function rsaDecrypt(encryptedBase64: string, privateKey: KeyObject | string): string {
  const key = typeof privateKey === "string" ? parsePrivateKeyPem(privateKey) : privateKey;
  try {
    const decrypted = privateDecrypt(
      {
        key,
        padding: constants.RSA_PKCS1_OAEP_PADDING,
        oaepHash: "sha256",
      },
      Buffer.from(encryptedBase64, "base64"),
    );
    return decrypted.toString("utf8");
  } catch (e) {
    throw new CryptoError("RSA decrypt failed", e);
  }
}

// ---- AES-GCM ----

export function aesEncrypt(plainText: string, aesKeyBase64: string, aad: string): EncryptResult {
  const keyBytes = Buffer.from(aesKeyBase64, "base64");
  if (keyBytes.length !== AES_KEY_SIZE) {
    throw new CryptoError("AES key must be 256 bits");
  }

  try {
    const iv = randomBytes(GCM_IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", keyBytes, iv);
    if (aad) {
      cipher.setAAD(Buffer.from(aad, "utf8"));
    }
    const ciphertext = Buffer.concat([cipher.update(plainText, "utf8"), cipher.final()]);
    const tag = cipher.getAuthTag();

    const tagIv = Buffer.concat([tag, iv]);
    const combined = Buffer.concat([ciphertext, tag, iv]);

    return {
      ciphertext: ciphertext.toString("base64"),
      tagIv: tagIv.toString("base64"),
      combined: combined.toString("base64"),
    };
  } catch (e) {
    if (e instanceof CryptoError) throw e;
    throw new CryptoError("AES encrypt failed", e);
  }
}

/** 解密 combined：base64(ciphertext + tag + iv)。 */
export function aesDecryptCombined(
  combinedBase64: string,
  aesKeyBase64: string,
  aad: string,
): Buffer {
  const keyBytes = Buffer.from(aesKeyBase64, "base64");
  if (keyBytes.length !== AES_KEY_SIZE) {
    throw new CryptoError("AES key must be 256 bits");
  }
  try {
    const data = Buffer.from(combinedBase64, "base64");
    if (data.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
      throw new CryptoError("Combined data too short");
    }
    const sealedLen = data.length - GCM_IV_LENGTH;
    const sealed = data.subarray(0, sealedLen);
    const iv = data.subarray(sealedLen);
    const ciphertext = sealed.subarray(0, sealed.length - GCM_TAG_LENGTH);
    const tag = sealed.subarray(sealed.length - GCM_TAG_LENGTH);
    return gcmDecrypt(ciphertext, tag, iv, keyBytes, aad);
  } catch (e) {
    if (e instanceof CryptoError) throw e;
    throw new CryptoError("AES decrypt failed", e);
  }
}

/** 解密分离的 ciphertext + tagIv（tag+iv）。 */
export function aesDecryptCiphertextAndTag(
  ciphertextBase64: string,
  tagIvBase64: string,
  aesKeyBase64: string,
  aad: string,
): Buffer {
  const keyBytes = Buffer.from(aesKeyBase64, "base64");
  if (keyBytes.length !== AES_KEY_SIZE) {
    throw new CryptoError("AES key must be 256 bits");
  }
  try {
    const ciphertext = Buffer.from(ciphertextBase64, "base64");
    const tagIv = Buffer.from(tagIvBase64, "base64");
    if (tagIv.length !== GCM_TAG_LENGTH + GCM_IV_LENGTH) {
      throw new CryptoError(`tagIv must be ${GCM_TAG_LENGTH + GCM_IV_LENGTH} bytes`);
    }
    const tag = tagIv.subarray(0, GCM_TAG_LENGTH);
    const iv = tagIv.subarray(GCM_TAG_LENGTH);
    return gcmDecrypt(ciphertext, tag, iv, keyBytes, aad);
  } catch (e) {
    if (e instanceof CryptoError) throw e;
    throw new CryptoError("AES decrypt failed", e);
  }
}

function gcmDecrypt(
  ciphertext: Buffer,
  tag: Buffer,
  iv: Buffer,
  keyBytes: Buffer,
  aad: string,
): Buffer {
  const decipher = createDecipheriv("aes-256-gcm", keyBytes, iv);
  if (aad) {
    decipher.setAAD(Buffer.from(aad, "utf8"));
  }
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

/** 用 AES-GCM 作为 MAC（空 ciphertext + tagIv）。 */
export function verifySign(signBase64: string, aesKeyBase64: string, aad: string): boolean {
  try {
    aesDecryptCiphertextAndTag("", signBase64, aesKeyBase64, aad);
    return true;
  } catch {
    return false;
  }
}

/** 构建 AAD：按 key 排序后 key=value 用 & 连接；空值跳过。 */
export function buildAad(params: Record<string, string | undefined | null>): string {
  const sorted = Object.keys(params)
    .filter((k) => {
      const v = params[k];
      return v != null && v !== "";
    })
    .sort();
  return sorted.map((k) => `${k}=${params[k]}`).join("&");
}
