/**
 * 跨域共享的纯函数 helper。
 *
 * 这些工具被多个域模块（dict / menu-api / user-role / login-log / api-log / task
 * 等）引用，单独抽出避免域模块之间互相依赖，也避免在 barrel 里产生循环引用。
 * mock-data.ts 通过 `export * from './mock-shared'` 再导出，handler 侧 import 路径不变。
 */

/** 当前时间的 ISO 字符串（mock 通用时间戳来源）。 */
export function isoNow(): string {
  return new Date().toISOString();
}

/** demo 占位密码哈希：不真实加密，仅加前缀便于辨识。 */
export function placeholderHash(plain: string): string {
  return `demo$bcrypt$${plain}`;
}

/** n 小时前的 ISO 时间字符串（mock 日志种子用）。 */
export function hoursAgo(h: number): string {
  return new Date(Date.now() - h * 3600_000).toISOString();
}

/** n 天前的 ISO 时间字符串（mock 归档日志种子用）。 */
export function daysAgo(d: number): string {
  return new Date(Date.now() - d * 86_400_000).toISOString();
}

/** 生成 mock 请求 ID（前缀 + 序号 + 时间戳 base36）。 */
export function makeRequestId(prefix: string, n: number): string {
  return `${prefix}-${String(n).padStart(4, "0")}-${Date.now().toString(36)}`;
}

/** 简易 UA 解析（mock 用，非完整 parser）。 */
export function parseUserAgent(ua: string): {
  browser_name: string;
  browser_version: string;
  os_name: string;
  os_version: string;
} {
  let browser_name = "";
  let browser_version = "";
  let os_name = "";
  let os_version = "";

  const edge = ua.match(/Edg\/([\d.]+)/);
  const chrome = ua.match(/Chrome\/([\d.]+)/);
  const firefox = ua.match(/Firefox\/([\d.]+)/);
  const safari = ua.match(/Version\/([\d.]+).*Safari/);
  if (edge) {
    browser_name = "Edge";
    browser_version = edge[1] ?? "";
  } else if (chrome) {
    browser_name = "Chrome";
    browser_version = chrome[1] ?? "";
  } else if (firefox) {
    browser_name = "Firefox";
    browser_version = firefox[1] ?? "";
  } else if (safari) {
    browser_name = "Safari";
    browser_version = safari[1] ?? "";
  } else if (ua) {
    browser_name = "Unknown";
  }

  const win = ua.match(/Windows NT ([\d.]+)/);
  const mac = ua.match(/Mac OS X ([\d_]+)/);
  const android = ua.match(/Android ([\d.]+)/);
  const ios = ua.match(/OS ([\d_]+) like Mac OS X/);
  if (win) {
    os_name = "Windows";
    os_version = win[1] === "10.0" ? "10/11" : (win[1] ?? "");
  } else if (mac) {
    os_name = "macOS";
    os_version = (mac[1] ?? "").replace(/_/g, ".");
  } else if (android) {
    os_name = "Android";
    os_version = android[1] ?? "";
  } else if (ios) {
    os_name = "iOS";
    os_version = (ios[1] ?? "").replace(/_/g, ".");
  } else if (/Linux/.test(ua)) {
    os_name = "Linux";
  }

  return { browser_name, browser_version, os_name, os_version };
}
