/**
 * 客户端侧免加密路径（对齐 Java / mock 白名单）。
 * 登录 `/api/auth/login` 不在白名单。
 */

const WHITELIST_EXACT = new Set([
  '/api/encrypt/public/key',
  '/doc.html',
  // 兼容 baseURL 已含 /api 时 url 为 /encrypt/public/key（对齐 React）
  '/encrypt/public/key',
  '/error',
  '/favicon.ico',
]);

export function normalizePath(path: string): string {
  if (!path) return '/';
  let p = path;
  try {
    if (/^https?:\/\//i.test(path)) {
      p = new URL(path).pathname;
    }
  } catch {
    // keep raw
  }
  const q = p.indexOf('?');
  if (q !== -1) p = p.slice(0, q);
  if (!p.startsWith('/')) p = `/${p}`;
  if (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1);
  return p;
}

/**
 * 将 axios `url` + `baseURL` 合成用于白名单匹配的路径。
 * 例：baseURL=`/api` + url=`/auth/login` → `/api/auth/login`
 */
export function resolveRequestPath(url?: string, baseURL?: string): string {
  if (!url) return normalizePath(baseURL ?? '/');
  if (/^https?:\/\//i.test(url)) {
    return normalizePath(url);
  }
  const base = (baseURL ?? '').replace(/\/$/, '');
  const path = url.startsWith('/') ? url : `/${url}`;
  if (!base) return normalizePath(path);
  // base 已是绝对 URL
  if (/^https?:\/\//i.test(base)) {
    try {
      return normalizePath(
        new URL(path, base.endsWith('/') ? base : `${base}/`).pathname,
      );
    } catch {
      return normalizePath(path);
    }
  }
  // base 为路径前缀（如 /api）
  if (path.startsWith(base)) {
    return normalizePath(path);
  }
  return normalizePath(`${base}${path}`);
}

export function isSecurityWhitelisted(path: string): boolean {
  const normalized = normalizePath(path);
  if (WHITELIST_EXACT.has(normalized)) return true;
  if (normalized === '/v3/api-docs' || normalized.startsWith('/v3/api-docs/')) {
    return true;
  }
  if (normalized === '/swagger-ui' || normalized.startsWith('/swagger-ui/')) {
    return true;
  }
  if (normalized === '/api/altcha' || normalized.startsWith('/api/altcha/')) {
    return true;
  }
  // 进页/切语言拉取翻译包：免加密与独立 Sign（对齐 Java / mock）
  if (
    normalized === '/api/public/i18n' ||
    normalized.startsWith('/api/public/i18n/') ||
    normalized === '/public/i18n' ||
    normalized.startsWith('/public/i18n/')
  ) {
    return true;
  }
  if (normalized === '/api/health' || normalized.startsWith('/api/health/')) {
    return true;
  }
  if (normalized === '/actuator' || normalized.startsWith('/actuator/')) {
    return true;
  }
  if (normalized.startsWith('/doc.html/')) return true;
  return false;
}

/** multipart / SSE 跳过 body 加解密。 */
export function shouldSkipBodyCrypto(options: {
  contentType?: null | string;
  data?: unknown;
  path?: string;
}): boolean {
  if (typeof FormData !== 'undefined' && options.data instanceof FormData) {
    return true;
  }
  const ct = options.contentType?.toLowerCase() ?? '';
  if (ct.includes('multipart/form-data')) {
    return true;
  }
  const path = options.path ? normalizePath(options.path) : '';
  if (path.endsWith('/events')) {
    return true;
  }
  return false;
}
