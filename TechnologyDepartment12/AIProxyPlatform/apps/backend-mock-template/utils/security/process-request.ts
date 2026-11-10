/**
 * 请求安全协议处理（Timestamp → Nonce → Encrypt | Sign → Language）。
 * 纯函数风格，便于 seam 测试；middleware 只做 h3 适配。
 */

import {
  aesDecryptCiphertextAndTag,
  aesEncrypt,
  buildAad,
  CryptoError,
  rsaDecrypt,
  verifySign,
} from "./crypto";
import type { SecurityConfig } from "./config";
import { resolveNonceExpireMs } from "./config";
import { SECURITY_HEADERS, SIGN_DATA_AAD_KEY } from "./headers";
import type { NonceStore } from "./nonce-store";
import { isSecurityWhitelisted } from "./path-matcher";
import { securityErrorBody, SecurityResultCode, type SecurityErrorCode } from "./result-codes";

export interface SecurityRequestInput {
  method: string;
  path: string;
  headers: Record<string, string | undefined>;
  /** 原始 body 字符串；无 body 时为 "" */
  body: string;
  /** query 参数（已解析的单值 map） */
  query?: Record<string, string | undefined>;
  contentType?: string | null;
  nowMs?: number;
}

export interface SecurityRequestOk {
  ok: true;
  /** 业务应使用的 body（已解密或明文） */
  body: string;
  /** 若请求带加密密钥且 Encrypt 开，响应需加密 */
  responseAesKeyBase64?: string;
  language?: string;
}

export interface SecurityRequestErr {
  ok: false;
  body: ReturnType<typeof securityErrorBody>;
}

export type SecurityRequestResult = SecurityRequestOk | SecurityRequestErr;

export interface ProcessSecurityDeps {
  config: SecurityConfig;
  privateKeyPem: string;
  nonceStore: NonceStore;
}

function firstHeader(
  headers: Record<string, string | undefined>,
  ...names: string[]
): string | undefined {
  for (const name of names) {
    // HTTP 头大小写不敏感
    const direct = headers[name] ?? headers[name.toLowerCase()];
    if (direct != null && direct !== "") return direct;
    // 扫描 keys
    const found = Object.entries(headers).find(
      ([k, v]) => k.toLowerCase() === name.toLowerCase() && v != null && v !== "",
    );
    if (found) return found[1];
  }
  return undefined;
}

function err(code: SecurityErrorCode): SecurityRequestErr {
  return { ok: false, body: securityErrorBody(code) };
}

function isMultipart(contentType?: string | null): boolean {
  return !!contentType && contentType.toLowerCase().startsWith("multipart/form-data");
}

function isSsePath(path: string): boolean {
  return path.endsWith("/events");
}

function resolveLanguage(headers: Record<string, string | undefined>): string | undefined {
  const xLang = firstHeader(headers, SECURITY_HEADERS.LANGUAGE);
  if (xLang) {
    const t = xLang.trim();
    if (t) return t;
  }
  const accept = firstHeader(headers, "Accept-Language");
  if (!accept) return undefined;
  const first = accept.split(",")[0]?.trim() ?? "";
  const semi = first.indexOf(";");
  const tag = (semi >= 0 ? first.slice(0, semi) : first).trim();
  return tag || undefined;
}

function timestampHeaderName(headers: Record<string, string | undefined>): string {
  if (firstHeader(headers, SECURITY_HEADERS.REQUEST_TIMESTAMP)) {
    return SECURITY_HEADERS.REQUEST_TIMESTAMP;
  }
  return SECURITY_HEADERS.TIMESTAMP_LEGACY;
}

function buildRequestAad(
  headers: Record<string, string | undefined>,
  query: Record<string, string | undefined> | undefined,
): string {
  const params: Record<string, string | undefined> = {};
  const requestId = firstHeader(headers, SECURITY_HEADERS.REQUEST_ID);
  if (requestId) params[SECURITY_HEADERS.REQUEST_ID] = requestId;
  const ts = firstHeader(
    headers,
    SECURITY_HEADERS.REQUEST_TIMESTAMP,
    SECURITY_HEADERS.TIMESTAMP_LEGACY,
  );
  if (ts) params[timestampHeaderName(headers)] = ts;
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v != null && v !== "") params[k] = v;
    }
  }
  return buildAad(params);
}

function buildSignAad(
  headers: Record<string, string | undefined>,
  query: Record<string, string | undefined> | undefined,
  rawBody: string,
): string {
  const params: Record<string, string | undefined> = {};
  const requestId = firstHeader(headers, SECURITY_HEADERS.REQUEST_ID);
  if (requestId) params[SECURITY_HEADERS.REQUEST_ID] = requestId;
  const ts = firstHeader(
    headers,
    SECURITY_HEADERS.REQUEST_TIMESTAMP,
    SECURITY_HEADERS.TIMESTAMP_LEGACY,
  );
  if (ts) params[timestampHeaderName(headers)] = ts;
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v != null && v !== "") params[k] = v;
    }
  }
  if (rawBody.length > 0) {
    params[SIGN_DATA_AAD_KEY] = rawBody;
  }
  return buildAad(params);
}

/**
 * 处理安全协议。OPTIONS 应在外层直接跳过。
 */
export function processSecurityRequest(
  input: SecurityRequestInput,
  deps: ProcessSecurityDeps,
): SecurityRequestResult {
  const { config, privateKeyPem, nonceStore } = deps;
  const now = input.nowMs ?? Date.now();
  const path = input.path;
  const headers = input.headers;
  const rawBody = input.body ?? "";

  // ---- Timestamp ----
  if (config.timestampEnabled) {
    const tsHeader = firstHeader(
      headers,
      SECURITY_HEADERS.REQUEST_TIMESTAMP,
      SECURITY_HEADERS.TIMESTAMP_LEGACY,
    );
    if (tsHeader) {
      const timestamp = Number(tsHeader);
      if (!Number.isFinite(timestamp)) {
        return err(SecurityResultCode.REQUEST_ERROR);
      }
      if (Math.abs(now - timestamp) > config.timestampExpireMs) {
        return err(SecurityResultCode.REQUEST_EXPIRED);
      }
    }
  }

  // ---- Nonce ----
  if (config.nonceEnabled) {
    const requestId = firstHeader(headers, SECURITY_HEADERS.REQUEST_ID);
    if (requestId) {
      const ttl = resolveNonceExpireMs(config);
      if (!nonceStore.tryAcquire(requestId, ttl)) {
        return err(SecurityResultCode.REQUEST_NONCE_CONFLICT);
      }
    }
  }

  let body = rawBody;
  let responseAesKeyBase64: string | undefined;

  const skipBodyCrypto = isMultipart(input.contentType) || isSsePath(path);
  const whitelisted = isSecurityWhitelisted(path);

  // ---- Encrypt ----
  if (config.encryptEnabled && !skipBodyCrypto) {
    const encryptedKey = firstHeader(headers, SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY);
    const hasKey = !!encryptedKey;

    if (!hasKey) {
      if (!whitelisted) {
        return err(SecurityResultCode.REQUEST_ERROR);
      }
      // 白名单明文放行
    } else {
      let aesKeyBase64: string;
      try {
        aesKeyBase64 = rsaDecrypt(encryptedKey!, privateKeyPem);
      } catch {
        return err(SecurityResultCode.REQUEST_KEY_FAILED);
      }
      responseAesKeyBase64 = aesKeyBase64;

      if (rawBody.length > 0) {
        const sign = firstHeader(
          headers,
          SECURITY_HEADERS.REQUEST_SIGNATURE,
          SECURITY_HEADERS.SIGN_LEGACY,
        );
        if (!sign) {
          return err(SecurityResultCode.REQUEST_ERROR);
        }
        try {
          const aad = buildRequestAad(headers, input.query);
          body = aesDecryptCiphertextAndTag(rawBody, sign, aesKeyBase64, aad).toString("utf8");
        } catch (e) {
          if (e instanceof CryptoError) {
            return err(SecurityResultCode.REQUEST_KEY_FAILED);
          }
          return err(SecurityResultCode.REQUEST_KEY_FAILED);
        }
      }
    }
  } else if (!config.encryptEnabled && config.signEnabled && !skipBodyCrypto && !whitelisted) {
    // ---- Sign（仅 Encrypt 关）----
    const encryptedKey = firstHeader(headers, SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY);
    const sign = firstHeader(
      headers,
      SECURITY_HEADERS.REQUEST_SIGNATURE,
      SECURITY_HEADERS.SIGN_LEGACY,
    );
    if (!encryptedKey || !sign) {
      return err(SecurityResultCode.REQUEST_ERROR);
    }
    let aesKeyBase64: string;
    try {
      aesKeyBase64 = rsaDecrypt(encryptedKey, privateKeyPem);
    } catch {
      return err(SecurityResultCode.REQUEST_KEY_FAILED);
    }
    const aad = buildSignAad(headers, input.query, rawBody);
    if (!verifySign(sign, aesKeyBase64, aad)) {
      return err(SecurityResultCode.REQUEST_SIGN_FAILED);
    }
  }

  let language: string | undefined;
  if (config.languageEnabled) {
    language = resolveLanguage(headers);
  }

  return {
    ok: true,
    body,
    responseAesKeyBase64,
    language,
  };
}

/** 加密响应体（Encrypt 路径有会话 AES key 时）。响应 AAD 为空串，对齐 Java EncryptFilter。 */
export function encryptResponseBody(plainResponse: string, aesKeyBase64: string): string {
  return aesEncrypt(plainResponse, aesKeyBase64, "").combined;
}
