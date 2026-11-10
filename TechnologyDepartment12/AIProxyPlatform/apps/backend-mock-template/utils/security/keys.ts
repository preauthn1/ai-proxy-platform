/**
 * 全局 RSA 密钥对（进程内缓存）。
 * 可用 SECURITY_RSA_PUBLIC_KEY / SECURITY_RSA_PRIVATE_KEY 注入固定开发密钥；
 * hybrid 下也可由 {@link setEncryptKeyPair} 注入从 java 拉取的密钥对。
 */

import { generateRsaKeyPair, type RsaKeyPairPem } from "./crypto";

let cached: RsaKeyPairPem | null = null;

export function getEncryptKeyPair(): RsaKeyPairPem {
  if (cached) return cached;

  const envPub = process.env.SECURITY_RSA_PUBLIC_KEY?.trim();
  const envPriv = process.env.SECURITY_RSA_PRIVATE_KEY?.trim();
  if (envPub && envPriv) {
    cached = {
      publicKeyBase64: envPub.replace(/\\n/g, "\n").includes("BEGIN")
        ? spkiPemToBase64(envPub.replace(/\\n/g, "\n"))
        : envPub.replace(/\s/g, ""),
      privateKeyPem: envPriv.replace(/\\n/g, "\n"),
    };
    return cached;
  }

  cached = generateRsaKeyPair();
  return cached;
}

/**
 * 注入密钥对（覆盖当前缓存）。
 * hybrid：java-key-sync 在本地生成前调用；须在首次业务解密前完成。
 */
export function setEncryptKeyPair(pair: RsaKeyPairPem): void {
  cached = pair;
}

/** 测试用：注入或重置密钥对。 */
export function setEncryptKeyPairForTest(pair: RsaKeyPairPem | null): void {
  cached = pair;
}

function spkiPemToBase64(pem: string): string {
  return pem
    .replace(/-----BEGIN PUBLIC KEY-----/g, "")
    .replace(/-----END PUBLIC KEY-----/g, "")
    .replace(/\s/g, "");
}
