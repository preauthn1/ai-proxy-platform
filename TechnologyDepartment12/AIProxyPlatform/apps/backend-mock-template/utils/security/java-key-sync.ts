/**
 * 可选：从 java-admin（仅 dev）拉取 RSA 密钥对并注入 mock，
 * 使前端用 Java 公钥加密的请求可在 mock 上解密（避免 /menu/all 等 1006 密钥错误）。
 *
 * 环境变量 `SECURITY_JAVA_KEY_PAIR_URL`：
 * - **未配置 / 空**：不访问 java，使用本地生成或 SECURITY_RSA_* 固定钥
 * - **已配置**：启动后首次安全中间件请求时 GET 该地址并 adopt
 *
 * 若已设置 SECURITY_RSA_PUBLIC_KEY + SECURITY_RSA_PRIVATE_KEY，则不拉取。
 */

import type { RsaKeyPairPem } from "./crypto";
import { setEncryptKeyPair } from "./keys";

const DEFAULT_TIMEOUT_MS = 3000;

let syncDone = false;
let pending: Promise<boolean> | null = null;

/**
 * java 完整密钥对 URL。
 * 未设置或空字符串 → 不请求。
 */
export function getJavaKeyPairUrl(): string {
  const raw = process.env.SECURITY_JAVA_KEY_PAIR_URL;
  if (raw === undefined || raw === "") return "";
  return raw.trim();
}

function javaKeyPairTimeoutMs(): number {
  const raw = process.env.SECURITY_JAVA_KEY_PAIR_TIMEOUT_MS;
  if (raw === undefined || raw === "") return DEFAULT_TIMEOUT_MS;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return DEFAULT_TIMEOUT_MS;
  return Math.floor(n);
}

/** 是否已通过 env 固定密钥（跳过 Java 拉取）。 */
export function hasFixedRsaEnvKeys(): boolean {
  const pub = process.env.SECURITY_RSA_PUBLIC_KEY?.trim();
  const priv = process.env.SECURITY_RSA_PRIVATE_KEY?.trim();
  return !!(pub && priv);
}

/**
 * 确保已尝试从 Java 同步密钥（进程内一次；并发去重）。
 * @returns true 表示已采用 Java 密钥；false 表示关闭/失败/已有固定 env
 */
export async function ensureJavaKeyPairSynced(): Promise<boolean> {
  if (syncDone) return false;
  if (hasFixedRsaEnvKeys()) {
    syncDone = true;
    console.info("[security] 已配置 SECURITY_RSA_*，跳过 java key pair 拉取");
    return false;
  }
  const url = getJavaKeyPairUrl();
  if (!url) {
    syncDone = true;
    // 启动 plugin 会打一次；middleware 再次调用时 syncDone 已 true，不再刷屏
    return false;
  }
  if (pending) return pending;

  pending = (async () => {
    try {
      console.info("[security] 正在请求 java key pair:", url);
      const pair = await fetchJavaKeyPair(url);
      if (!pair) return false;
      setEncryptKeyPair(pair);
      console.info("[security] 已从 java 同步 encrypt key pair:", url);
      return true;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      console.warn("[security] 从 java 同步 key pair 失败，使用本地密钥:", msg);
      return false;
    } finally {
      syncDone = true;
      pending = null;
    }
  })();

  return pending;
}

interface JavaKeyPairResult {
  code?: number;
  data?: { publicKey?: string; privateKey?: string };
}

async function fetchJavaKeyPair(url: string): Promise<RsaKeyPairPem | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), javaKeyPairTimeoutMs());
  try {
    const res = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json" },
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
    if (!publicKeyRaw?.trim() || !privateKeyRaw?.trim()) {
      throw new Error("missing publicKey/privateKey in data");
    }
    const publicKey = publicKeyRaw.trim();
    // 私钥 PEM 保留原文换行（勿 trim 掉尾部 \\n，否则与 Java 导出不完全一致）
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

/** 测试用：重置同步状态。 */
export function resetJavaKeyPairSyncForTest(): void {
  syncDone = false;
  pending = null;
}
