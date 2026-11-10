import { defineEventHandler } from "h3";

import {
  ensureTokenAdopted,
  extractBearerToken,
  getJavaIntrospectUrl,
  isMixtureMode,
} from "~/utils/session-utils";

/**
 * 鉴权桥（按 AUTH_MODE）：
 *
 * - `mock`（默认）：纯 mock，本中间件默认不做事；仅当显式配置
 *   `AUTH_JAVA_INTROSPECT_URL` 时，才对本地未知 token 尝试 java 内省。
 * - `mixture`：与 java 交叉联调，**不校验 token、不内省**
 *   （避免请求 java `/api/user/info` 因缺少 Encrypt 头被拒）。
 *   业务侧 `verifyAccessToken` 直接放行并使用 `AUTH_JAVA_USER_FALLBACK`。
 */
export default defineEventHandler(async (event) => {
  if (event.method === "OPTIONS") return;
  const path = event.path ?? "";
  if (!path.startsWith("/api/")) return;

  // mixture：不验证 token、不调 java
  if (isMixtureMode()) return;

  // mock：未配置内省 URL 则跳过（纯本地会话）
  if (!getJavaIntrospectUrl()) return;

  const token = extractBearerToken(event);
  if (!token) return;

  await ensureTokenAdopted(token);
});
