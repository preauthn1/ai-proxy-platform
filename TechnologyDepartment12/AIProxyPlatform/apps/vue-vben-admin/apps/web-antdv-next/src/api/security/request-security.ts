/**
 * 请求安全协议 seam：根据分项开关注入头、加密/签名请求、解密响应。
 * 纯函数 + 可注入 deps，便于单测。
 */

import type { SecurityFlags } from './config';

import {
  aesDecrypt,
  aesEncrypt,
  generateAesKey,
  rsaEncrypt,
  uriSort,
} from '#/utils/crypto';

import { SECURITY_HEADERS, SIGN_DATA_AAD_KEY } from './headers';
import {
  isSecurityWhitelisted,
  resolveRequestPath,
  shouldSkipBodyCrypto,
} from './path-matcher';

export interface SecureRequestConfig {
  baseURL?: string;
  data?: unknown;
  headers?: Record<string, unknown>;
  method?: string;
  params?: Record<string, unknown> | string | URLSearchParams;
  /** axios meta：skipEncrypt 强制跳过 body 加解密 */
  meta?: { skipEncrypt?: boolean };
  url?: string;
  /** Content-Type，用于识别 multipart */
  contentType?: null | string;
  /** 语言码，Language 开时写入 X-Language */
  language?: string;
}

export interface SecureRequestResult {
  headers: Record<string, unknown>;
  data?: unknown;
  aesKey?: CryptoKey;
  /** 加密响应需按 text 接收，避免 axios 破坏 base64 */
  responseType?: 'text';
  /** 非 GET 加密后 body 已是 base64 字符串，禁止 axios 再 JSON 序列化 */
  rawBody?: boolean;
}

export interface RequestSecurityDeps {
  aesEncrypt: typeof aesEncrypt;
  aesDecrypt: typeof aesDecrypt;
  generateAesKey: typeof generateAesKey;
  rsaEncrypt: typeof rsaEncrypt;
  ensurePublicKey: () => Promise<string>;
  getPublicCryptoKey: () => Promise<CryptoKey | undefined>;
  now?: () => number;
  nonce?: () => string;
}

/**
 * 将 query params 规范为「签名 AAD 用」的单值 map。
 *
 * 对齐 Java SignFilter / mock processSecurityRequest / React request-encryption：
 * 多值参数只取**第一个**非空值。
 * 注意：axios 默认会把数组序列化成 `typeCode[]=a&typeCode[]=b`，若 AAD 用
 * `String(array)`（得 `a,b`）或 key 名不一致，服务端会报 1008 签名错误。
 * 客户端应配合 `paramsSerializer: 'repeat'` 发出 `typeCode=a&typeCode=b`。
 */
function normalizeParams(
  params: Record<string, unknown> | string | undefined | URLSearchParams,
): Record<string, string> {
  if (!params) return {};

  if (typeof params === 'string') {
    return Object.fromEntries(new URLSearchParams(params));
  }

  if (params instanceof URLSearchParams) {
    return Object.fromEntries(params.entries());
  }

  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    if (Array.isArray(v)) {
      // 与服务端 putQueryParams / mock 扁平化一致：仅首个非空值进入 AAD
      const first = v.find(
        (item) => item !== undefined && item !== null && item !== '',
      );
      if (first === undefined) continue;
      out[k] = String(first);
      continue;
    }
    out[k] = String(v);
  }
  return out;
}

function defaultNonce(): string {
  return Math.random().toString(36).slice(2, 18);
}

function isGetMethod(method?: string): boolean {
  return (method ?? 'get').toUpperCase() === 'GET';
}

function parseMaybeJsonData(data: unknown): unknown {
  if (typeof data !== 'string') return data;
  try {
    return JSON.parse(data);
  } catch {
    return data;
  }
}

function bodyAsSignString(data: unknown): string {
  if (data === undefined || data === null) return '';
  if (typeof data === 'string') return data;
  return JSON.stringify(data);
}

/**
 * 根据安全开关处理单次请求配置（注入头 / 加密 / 独立签名）。
 */
export async function applyRequestSecurity(
  config: SecureRequestConfig,
  flags: SecurityFlags,
  deps: RequestSecurityDeps,
): Promise<SecureRequestResult> {
  const headers: Record<string, unknown> = { ...config.headers };
  const path = resolveRequestPath(config.url, config.baseURL);
  const whitelisted = isSecurityWhitelisted(path);
  const skipBody =
    config.meta?.skipEncrypt === true ||
    shouldSkipBodyCrypto({
      contentType:
        config.contentType ??
        (typeof headers['Content-Type'] === 'string'
          ? headers['Content-Type']
          : typeof headers['content-type'] === 'string'
            ? (headers['content-type'] as string)
            : null),
      data: config.data,
      path,
    });

  if (flags.languageEnabled && config.language) {
    headers[SECURITY_HEADERS.LANGUAGE] = config.language;
  }

  const needTimestamp =
    flags.timestampEnabled || flags.encryptEnabled || flags.signEnabled;
  const needNonce =
    flags.nonceEnabled || flags.encryptEnabled || flags.signEnabled;

  const timestamp = deps.now?.() ?? Date.now();
  const requestId = deps.nonce?.() ?? defaultNonce();

  if (needTimestamp) {
    headers[SECURITY_HEADERS.REQUEST_TIMESTAMP] = String(timestamp);
  }
  if (needNonce) {
    headers[SECURITY_HEADERS.REQUEST_ID] = requestId;
  }

  // 白名单 / multipart / 显式跳过：不加密不签名，仅带时间戳与 ID（若开关要求）
  if (whitelisted || skipBody) {
    return { headers, data: config.data };
  }

  // Encrypt 关且 Sign 关：明文
  if (!flags.encryptEnabled && !flags.signEnabled) {
    return { headers, data: config.data };
  }

  // Encrypt / 独立 Sign 开启时必须拿到公钥；失败直接抛错，避免静默明文被后端强制拒绝却难排查
  const publicKey = await deps.ensurePublicKey();
  if (!publicKey) {
    throw new Error(
      '[request-security] 无法获取 RSA 公钥（/encrypt/public/key），加密/签名请求中止',
    );
  }
  const publicCryptoKey = await deps.getPublicCryptoKey();
  if (!publicCryptoKey) {
    throw new Error('[request-security] RSA 公钥导入失败，加密/签名请求中止');
  }

  const { key, keyBase64 } = await deps.generateAesKey();
  headers[SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY] = await deps.rsaEncrypt(
    keyBase64,
    publicCryptoKey,
  );

  const params = normalizeParams(config.params);
  const aadBase: Record<string, unknown> = {
    ...params,
  };
  const requestIdHeader = headers[SECURITY_HEADERS.REQUEST_ID];
  if (requestIdHeader !== undefined && requestIdHeader !== null) {
    aadBase[SECURITY_HEADERS.REQUEST_ID] = requestIdHeader;
  }
  const timestampHeader = headers[SECURITY_HEADERS.REQUEST_TIMESTAMP];
  if (timestampHeader !== undefined && timestampHeader !== null) {
    aadBase[SECURITY_HEADERS.REQUEST_TIMESTAMP] = timestampHeader;
  }

  // ---- Encrypt ON：加密 body ----
  if (flags.encryptEnabled) {
    const aad = uriSort(aadBase);
    const isGet = isGetMethod(config.method);
    const payload = isGet ? undefined : parseMaybeJsonData(config.data);
    const aesData = await deps.aesEncrypt(key, aad, payload);
    headers[SECURITY_HEADERS.REQUEST_SIGNATURE] = aesData.TagIv;

    const encryptedBody =
      isGet || aesData.Ciphertext === '' ? config.data : aesData.Ciphertext;

    return {
      headers,
      data: encryptedBody,
      aesKey: key,
      responseType: 'text',
      rawBody: !isGet && typeof encryptedBody === 'string',
    };
  }

  // ---- Sign only（Encrypt 关）：body 明文，签名空 payload + signData AAD ----
  const signAadParams: Record<string, unknown> = { ...aadBase };
  const signBody = bodyAsSignString(config.data);
  if (signBody.length > 0) {
    signAadParams[SIGN_DATA_AAD_KEY] = signBody;
  }
  const signAad = uriSort(signAadParams);
  const signData = await deps.aesEncrypt(key, signAad, undefined);
  headers[SECURITY_HEADERS.REQUEST_SIGNATURE] = signData.TagIv;

  return {
    headers,
    data: config.data,
    aesKey: key,
  };
}

/**
 * 若响应标记加密则解密；否则原样返回 data。
 */
function tryParseJson(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value;
  }
  const trimmed = value.trim();
  if (
    !(
      (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))
    )
  ) {
    return value;
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    return value;
  }
}

export async function decryptResponseData(
  options: {
    aesKey?: CryptoKey | null;
    data: unknown;
    isEncrypted: boolean;
  },
  deps: Pick<RequestSecurityDeps, 'aesDecrypt'>,
): Promise<unknown> {
  if (!options.isEncrypted || !options.aesKey) {
    // Encrypt 请求常设 responseType=text；未加密时 body 仍是 JSON 字符串，需还原对象
    return tryParseJson(options.data);
  }
  const encryptedText =
    typeof options.data === 'string'
      ? options.data
      : JSON.stringify(options.data);
  const decryptedText = await deps.aesDecrypt(
    encryptedText,
    options.aesKey,
    '',
  );
  return tryParseJson(decryptedText);
}

export function isResponseEncrypted(
  headers: null | Record<string, unknown> | undefined,
): boolean {
  if (!headers) return false;
  const value =
    headers[SECURITY_HEADERS.RESPONSE_IS_ENCRYPT] ??
    headers['x-response-is-encrypt'] ??
    headers['X-Response-Is-Encrypt'];
  return String(value).toLowerCase() === 'true';
}
