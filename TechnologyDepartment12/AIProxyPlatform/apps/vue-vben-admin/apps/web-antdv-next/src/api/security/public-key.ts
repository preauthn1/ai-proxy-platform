/**
 * RSA 公钥缓存与拉取（走裸 fetch，避免请求拦截器递归加密）。
 *
 * 登录后服务端下发「会话专属」公钥，请求加密/签名须用该钥；刷新后内存会丢，
 * 因此同步持久化到 localStorage / sessionStorage，避免再次拉全局公钥导致
 * 服务端用会话私钥解不开（Sign/Encrypt RSA decrypt failed）。
 */

import { importRsaPublicKey } from '#/utils/crypto';

/** 与 accessToken 同生命周期的会话/全局公钥存储键 */
const STORAGE_KEY = 'encrypt-public-key';

let cachedPublicKeyBase64 = '';
let cachedPublicCryptoKey: CryptoKey | null = null;
let inflight: null | Promise<string> = null;

function readPersistedPublicKey(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  try {
    return (
      window.localStorage.getItem(STORAGE_KEY) ||
      window.sessionStorage.getItem(STORAGE_KEY) ||
      ''
    );
  } catch {
    return '';
  }
}

function writePersistedPublicKey(publicKey: string) {
  if (typeof window === 'undefined' || !publicKey) {
    return;
  }
  try {
    window.localStorage.setItem(STORAGE_KEY, publicKey);
    window.sessionStorage.setItem(STORAGE_KEY, publicKey);
  } catch {
    // quota / 隐私模式：仅内存缓存
  }
}

function removePersistedPublicKey() {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.removeItem(STORAGE_KEY);
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

/** 从 storage 恢复到内存（若尚未加载） */
function hydrateFromStorage(): string {
  if (cachedPublicKeyBase64) {
    return cachedPublicKeyBase64;
  }
  const stored = readPersistedPublicKey();
  if (stored) {
    cachedPublicKeyBase64 = stored;
    cachedPublicCryptoKey = null;
  }
  return cachedPublicKeyBase64;
}

export function clearCachedPublicKey() {
  cachedPublicKeyBase64 = '';
  cachedPublicCryptoKey = null;
  inflight = null;
  removePersistedPublicKey();
}

export function getCachedPublicKey(): string {
  return hydrateFromStorage();
}

export function setCachedPublicKey(publicKey: string) {
  if (!publicKey) {
    return;
  }
  if (publicKey !== cachedPublicKeyBase64) {
    cachedPublicKeyBase64 = publicKey;
    cachedPublicCryptoKey = null;
  }
  writePersistedPublicKey(publicKey);
}

/**
 * 拉取公钥：`GET {apiBase}/encrypt/public/key` → Result{ data: { publicKey } }
 * 仅在本地无缓存（含 storage）时调用。
 */
export async function fetchPublicKey(apiBase: string): Promise<string> {
  const base = (apiBase || '/api').replace(/\/$/, '');
  const url = `${base}/encrypt/public/key`;
  const response = await fetch(url, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    return '';
  }
  const res = (await response.json()) as {
    data?: { publicKey?: string };
  };
  return res?.data?.publicKey || '';
}

export interface EnsurePublicKeyOptions {
  /**
   * 强制重新拉取全局公钥，忽略内存/storage 缓存。
   * 用于未登录（登录页 / 重登）场景：storage 中可能残留上一会话专属公钥，
   * 若直接复用会导致服务端用全局私钥解不开（Sign/Encrypt RSA decrypt failed）。
   */
  force?: boolean;
}

/**
 * 确保本地有公钥。
 * 默认优先：内存 → 持久化 storage →（仅皆无时）裸 fetch 全局公钥。
 * 已登录刷新时不得覆盖 storage 中的会话公钥。
 * force 时跳过缓存，始终 GET /encrypt/public/key。
 */
export async function ensurePublicKey(
  apiBase: string,
  options?: EnsurePublicKeyOptions,
): Promise<string> {
  if (options?.force) {
    // 丢弃内存缓存以便重拉；storage 在拿到新钥后再覆盖
    cachedPublicKeyBase64 = '';
    cachedPublicCryptoKey = null;
  } else {
    const local = hydrateFromStorage();
    if (local) {
      return local;
    }
  }

  if (inflight) {
    return inflight;
  }

  const base = apiBase || '/api';
  inflight = (async () => {
    try {
      const key = await fetchPublicKey(base);
      if (key) {
        // 登录前全局钥也写入 storage；登录成功后会被会话钥覆盖
        setCachedPublicKey(key);
      }
      return key;
    } catch {
      return '';
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

/**
 * 登录前准备全局公钥：清会话残留后拉取 `/encrypt/public/key`。
 * 与 React 登录前走全局钥一致。
 */
export async function prepareGlobalPublicKey(apiBase: string): Promise<string> {
  clearCachedPublicKey();
  return ensurePublicKey(apiBase || '/api', { force: true });
}

export async function getPublicCryptoKey(): Promise<CryptoKey | undefined> {
  hydrateFromStorage();
  if (cachedPublicCryptoKey) {
    return cachedPublicCryptoKey;
  }
  if (!cachedPublicKeyBase64) {
    return undefined;
  }
  try {
    cachedPublicCryptoKey = await importRsaPublicKey(cachedPublicKeyBase64);
    return cachedPublicCryptoKey;
  } catch {
    return undefined;
  }
}
