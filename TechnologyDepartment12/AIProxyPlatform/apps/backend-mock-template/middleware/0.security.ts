/**
 * 请求安全中间件：Timestamp → Nonce → Encrypt|Sign → Language。
 * 与 Java 同协议；开关见 SECURITY_* 环境变量。
 */

import {
  defineEventHandler,
  getHeader,
  getQuery,
  getRequestURL,
  readRawBody,
  setResponseHeader,
  type H3Event,
} from "h3";

import { getSecurityConfig } from "~/utils/security/config";
import { ensureJavaKeyPairSynced } from "~/utils/security/java-key-sync";
import { ensureJavaSessionPrivateKey } from "~/utils/security/java-session-key-sync";
import { getEncryptKeyPair } from "~/utils/security/keys";
import { globalNonceStore } from "~/utils/security/nonce-store";
import { processSecurityRequest } from "~/utils/security/process-request";
import { extractBearerToken, getSessionPrivateKeyPem } from "~/utils/session-utils";

export interface SecurityContext {
  responseAesKeyBase64?: string;
  language?: string;
}

declare module "h3" {
  interface H3EventContext {
    security?: SecurityContext;
    language?: string;
  }
}

function headersToRecord(event: H3Event): Record<string, string | undefined> {
  const raw = event.node.req.headers;
  const out: Record<string, string | undefined> = {};
  for (const [k, v] of Object.entries(raw)) {
    if (Array.isArray(v)) out[k] = v[0];
    else out[k] = v ?? undefined;
  }
  return out;
}

export default defineEventHandler(async (event) => {
  if (event.method === "OPTIONS") return;

  const path = event.path ?? getRequestURL(event).pathname;
  // 仅处理 API 与文档/健康等安全相关路径；其它静态放行
  // 实际 mock 几乎全是 /api
  if (
    !path.startsWith("/api") &&
    !path.startsWith("/v3/") &&
    !path.startsWith("/swagger") &&
    !path.startsWith("/doc") &&
    !path.startsWith("/actuator")
  ) {
    return;
  }

  const config = getSecurityConfig();
  // 仅当 SECURITY_JAVA_KEY_PAIR_URL 已配置时才拉 java 全局密钥；否则本地钥
  await ensureJavaKeyPairSynced();
  const keys = getEncryptKeyPair();

  // 会话专属钥：本地 mock 登录会话 → 可选 SECURITY_JAVA_SESSION_KEY_URL → 回退全局
  const bearer = extractBearerToken(event);
  let privateKeyPem =
    getSessionPrivateKeyPem(bearer) ??
    (await ensureJavaSessionPrivateKey(bearer)) ??
    keys.privateKeyPem;

  let body = "";
  const contentType = getHeader(event, "content-type");
  const isPayload =
    event.method === "POST" ||
    event.method === "PUT" ||
    event.method === "PATCH" ||
    event.method === "DELETE";
  if (isPayload && !contentType?.toLowerCase().startsWith("multipart/form-data")) {
    try {
      const raw = await readRawBody(event, "utf8");
      body = raw ?? "";
    } catch {
      body = "";
    }
  }

  const queryRaw = getQuery(event);
  // 签名 AAD 用 query：多值取首项；axios 默认 brackets 的 key 形如 typeCode[]，
  // 剥掉尾部 [] 以对齐前端 AAD 的 typeCode 与 Java parameter 名习惯。
  const query: Record<string, string | undefined> = {};
  for (const [k, v] of Object.entries(queryRaw)) {
    const key = k.endsWith("[]") ? k.slice(0, -2) : k;
    if (!key) continue;
    const raw = Array.isArray(v) ? v[0] : v;
    if (raw == null || raw === "") continue;
    // 同名键已存在时保留先写入的值（与 Java values[0] 语义一致）
    if (query[key] !== undefined) continue;
    query[key] = String(raw);
  }

  const result = processSecurityRequest(
    {
      method: event.method,
      path,
      headers: headersToRecord(event),
      body,
      query,
      contentType,
    },
    {
      config,
      privateKeyPem,
      nonceStore: globalNonceStore,
    },
  );

  if (!result.ok) {
    // HTTP 200 + Result 业务码，对齐 Java
    setResponseHeader(event, "Content-Type", "application/json; charset=utf-8");
    return result.body;
  }

  // 注入解密后的 body，供下游 readBody 使用
  if (isPayload) {
    const payload = result.body !== body ? result.body : body;
    if (payload.length > 0 || result.body !== body) {
      (event as { _requestBody?: Buffer | string })._requestBody = Buffer.from(payload, "utf8");
      const req = event.node.req as unknown as {
        rawBody?: unknown;
        body?: unknown;
        [key: symbol]: unknown;
      };
      delete req[Symbol.for("h3ParsedBody")];
      delete req[Symbol.for("h3RawBody")];
      delete req.rawBody;
      delete req.body;
    }
  }

  event.context.security = {
    responseAesKeyBase64: result.responseAesKeyBase64,
    language: result.language,
  };

  if (result.language) {
    // 便于 handler / 日志读取
    event.context.language = result.language;
  }
});
