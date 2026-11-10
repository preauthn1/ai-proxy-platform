/**
 * ALTCHA PoW 服务端工具：签发 challenge + 校验 widget 提交的 payload。
 *
 * 纯自托管模式（无 ALTCHA Sentinel / SaaS），用 altcha-lib v2 在本地完成
 * proof-of-work 的生成与密码学验证，零外部网络依赖。
 *
 * @see https://altcha.org/docs/v2/server-integration/
 */
import { createChallenge, verifySolution } from "altcha-lib";
import { deriveKey } from "altcha-lib/algorithms/pbkdf2";
import type { Challenge } from "altcha-lib";

/**
 * HMAC 签名密钥：用于签发/校验 challenge。
 * 开发环境固定一个常量；生产应通过 env 注入且定期轮换。
 * 进程内存会话随进程重启清空，故 challenge 一次性消费也随进程一起重置。
 */
const ALTCHA_HMAC_SECRET = process.env.ALTCHA_HMAC_KEY ?? "altcha-dev-hmac-secret";

/** PoW 算法与成本（PBKDF2/SHA-256，cost=1000，dev 浏览器端 <0.5s 可解）。
 *  不传 counter → 用默认 keyPrefix="00"（1 字节），客户端暴力匹配 2^8~2^12 量级秒级完成；
 *  之前传 counter 会把 keyPrefix 覆盖成 16 字节，导致客户端永远解不完。*/
const ALGORITHM = "PBKDF2/SHA-256";
const COST = 1_000;

/** challenge 有效期 10 分钟（秒）。 */
const EXPIRES_SECONDS = 600;

/**
 * 已签发 challenge 的 signature 单次消费记录。
 * verify 成功后立即删除，阻止重放；mock 进程重启清空可接受。
 */
const consumedSignatures = new Set<string>();

/** 签发一个新 challenge。 */
export async function createAltchaChallenge(): Promise<Challenge> {
  return createChallenge({
    algorithm: ALGORITHM,
    cost: COST,
    // 不传 counter：保留默认 keyPrefix="00"，客户端按 1 字节前缀暴力搜索，
    // 搜索空间小、求解快；HMAC 签名仍保证 challenge 不可篡改。
    deriveKey,
    hmacSignatureSecret: ALTCHA_HMAC_SECRET,
    expiresAt: Math.floor(Date.now() / 1_000) + EXPIRES_SECONDS,
  });
}

/**
 * 校验 widget 提交的 payload。
 * @param payloadBase64 widget 隐藏字段 `altcha` 的值，Base64 编码的 JSON：
 *   `{ challenge: { parameters, signature }, solution: { counter, derivedKey } }`
 * @returns `true` 校验通过；`false` 失败（过期/签名无效/解不匹配/重放）。
 */
export async function verifyAltchaPayload(payloadBase64: string): Promise<boolean> {
  if (!payloadBase64) return false;

  let decoded: { challenge: Challenge; solution: unknown };
  try {
    const json = Buffer.from(payloadBase64, "base64").toString("utf8");
    decoded = JSON.parse(json);
  } catch {
    return false;
  }

  const { challenge, solution } = decoded ?? {};
  if (!challenge?.parameters || !challenge.signature || !solution) return false;

  // 重放保护：signature 一次性
  if (consumedSignatures.has(challenge.signature)) return false;

  let result: { verified: boolean };
  try {
    result = await verifySolution({
      challenge,
      solution: solution as never,
      deriveKey,
      hmacSignatureSecret: ALTCHA_HMAC_SECRET,
    });
  } catch {
    return false;
  }

  if (result.verified) {
    consumedSignatures.add(challenge.signature);
    return true;
  }
  return false;
}
