/**
 * I18n 翻译数据内容 hash，用于前端增量同步。
 */

import { createHash } from "node:crypto";

/**
 * 对翻译 key-value map 计算内容 hash（SHA256 前 8 位 hex）。
 * 前端请求时带上上次缓存的 hash，后端比对后返回 unchanged 或新数据。
 */
export function computeI18nHash(kvMap: Record<string, string>): string {
  const sorted = Object.keys(kvMap)
    .sort()
    .map((k) => `${k}=${kvMap[k]}`)
    .join("\n");
  return createHash("sha256").update(sorted).digest("hex").slice(0, 8);
}
