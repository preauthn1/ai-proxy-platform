/**
 * 可选：按 Bearer token 从 java-admin（仅 dev）拉取**会话专属** RSA 密钥对。
 * hybrid 下前端用 Java 登录返回的 publicKey 加密，业务 API 打到 mock 时需同私钥解密。
 *
 * 环境变量 `SECURITY_JAVA_SESSION_KEY_URL`：
 * - **未配置 / 空**：不访问 java，仅用本地 mock 登录会话钥或全局钥
 * - **已配置**：解密时本地无会话钥则 GET 该地址（携带 Authorization），adopt 后缓存
 *
 * 配置示例：
 * `SECURITY_JAVA_SESSION_KEY_URL=http://localhost:4080/api/encrypt/dev/session-key`
 *
 * 模式对齐 {@link ./java-key-sync.ts}（全局 `SECURITY_JAVA_KEY_PAIR_URL`）。
 */

import type { RsaKeyPairPem } from "./crypto";
import { adoptSessionEncryptKeys, getSessionPrivateKeyPem } from "../session-utils";

const DEFAULT_TIMEOUT_MS = 3000;

/** token → 已成功拉取过（避免重复请求失败刷屏） */
const fetchedOk = new Set<string>();
const pendingByToken = new Map<string, Promise<string | null>>();

export function getJavaSessionKeyUrl(): string {
  const raw = process.env.SECURITY_JAVA_SESSION_KEY_URL;
  if (raw === undefined || raw === "") return "";
  return raw.trim();
}

function javaSessionKeyTimeoutMs(): number {
  const raw = process.env.SECURITY_JAVA_SESSION_KEY_TIMEOUT_MS;
  if (raw === undefined || raw === "") return DEFAULT_TIMEOUT_MS;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return DEFAULT_TIMEOUT_MS;
  return Math.floor(n);
}

interface JavaKeyPairResult {
  code?: number;
  data?: { publicKey?: string; privateKey?: string };
}

/**
 * 确保 token 对应会话私钥可用：本地命中 → 直接返回；否则按 URL 拉取。
 * @returns 私钥 PEM，不可用时 null（调用方回退全局钥）
 */
export async function ensureJavaSessionPrivateKey(
  token: string | null | undefined,
): Promise<string | null> {
  if (!token) return null;

  const local = getSessionPrivateKeyPem(token);
  if (local) return local;

  const url = getJavaSessionKeyUrl();
  if (!url) return null;

  // 已成功 adopt 过但 get 仍空：不重复打（会话被清）
  if (fetchedOk.has(token) && !getSessionPrivateKeyPem(token)) {
    return null;
  }

  let pending = pendingByToken.get(token);
  if (!pending) {
    pending = (async () => {
      try {
        const pair = await fetchJavaSessionKey(url, token);
        if (!pair) return null;
        adoptSessionEncryptKeys(token, pair);
        fetchedOk.add(token);
        console.info("[security] 已从 java 同步 session encrypt key");
        return pair.privateKeyPem;
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        console.warn("[security] 从 java 同步 session key 失败:", msg);
        return null;
      } finally {
        pendingByToken.delete(token);
      }
    })();
    pendingByToken.set(token, pending);
  }
  return pending;
}

async function fetchJavaSessionKey(url: string, token: string): Promise<RsaKeyPairPem | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), javaSessionKeyTimeoutMs());
  try {
    const res = await fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
      },
      signal: controller.signal,
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const json = (await res.json()) as JavaKeyPairResult;
    if (json.code !== 0 && json.code !== undefined) {
      throw new Error(`biz code ${json.code}`);
    }
    const publicKeyRaw = json.data?.publicKey;
    const privateKeyRaw = json.data?.privateKey;
    if (!privateKeyRaw?.trim()) {
      throw new Error("missing privateKey in data");
    }
    const publicKey = (publicKeyRaw ?? "").trim();
    const privateKey = privateKeyRaw.replace(/\\n/g, "\n");
    return {
      publicKeyBase64: publicKey.includes("BEGIN")
        ? spkiPemToBase64(publicKey)
        : publicKey.replace(/\s/g, ""),
      privateKeyPem: privateKey,
    };
  } finally {
    clearTimeout(timer);
  }
}

function spkiPemToBase64(pem: string): string {
  return pem
    .replace(/-----BEGIN PUBLIC KEY-----/g, "")
    .replace(/-----END PUBLIC KEY-----/g, "")
    .replace(/\s/g, "");
}

/** 测试用：重置拉取状态 */
export function resetJavaSessionKeySyncForTest(): void {
  fetchedOk.clear();
  pendingByToken.clear();
}
