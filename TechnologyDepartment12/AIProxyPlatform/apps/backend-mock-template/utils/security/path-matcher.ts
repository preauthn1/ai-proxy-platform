/**
 * Encrypt / Sign 白名单（免强制加密与独立 Sign），对齐 Java SecurityPathMatcher。
 * 登录 /api/auth/login 不在白名单。
 */

const WHITELIST_EXACT = new Set([
  "/api/encrypt/public/key",
  // 与 Java 对齐（dev-only 接口；mock 本身不暴露私钥）
  "/api/encrypt/dev/key-pair",
  "/api/encrypt/dev/session-key",
  "/doc.html",
  "/favicon.ico",
  "/error",
]);

const WHITELIST_PREFIX = [
  "/api/altcha/",
  "/api/public/i18n/",
  "/doc.html/",
  "/swagger-ui/",
  "/v3/api-docs/",
  "/actuator/",
  "/api/health/",
];

/** Ant 风格简化：`/**` 前缀匹配。 */
export function isSecurityWhitelisted(path: string): boolean {
  const normalized = normalizePath(path);
  if (WHITELIST_EXACT.has(normalized)) return true;
  // /v3/api-docs 无尾斜杠
  if (normalized === "/v3/api-docs" || normalized.startsWith("/v3/api-docs/")) return true;
  if (normalized === "/swagger-ui" || normalized.startsWith("/swagger-ui/")) return true;
  if (normalized === "/api/altcha" || normalized.startsWith("/api/altcha/")) return true;
  // 公开翻译包：进页/未登录即可拉，免强制加密与独立 Sign
  if (normalized === "/api/public/i18n" || normalized.startsWith("/api/public/i18n/")) return true;
  if (normalized === "/api/health" || normalized.startsWith("/api/health/")) return true;
  if (normalized === "/actuator" || normalized.startsWith("/actuator/")) return true;
  for (const prefix of WHITELIST_PREFIX) {
    if (normalized.startsWith(prefix)) return true;
  }
  return false;
}

export function normalizePath(path: string): string {
  if (!path) return "/";
  // 去掉 query
  const q = path.indexOf("?");
  let p = q >= 0 ? path.slice(0, q) : path;
  if (!p.startsWith("/")) p = `/${p}`;
  // 去掉尾斜杠（根除外）
  if (p.length > 1 && p.endsWith("/")) p = p.slice(0, -1);
  return p;
}
