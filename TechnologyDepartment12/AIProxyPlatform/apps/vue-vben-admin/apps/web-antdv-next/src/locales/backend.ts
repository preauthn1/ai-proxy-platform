import type { SupportedLanguagesType } from '@vben/locales';

import { i18n as coreI18n } from '@vben/locales';

import { requestClient } from '#/api/request';

const CACHE_PREFIX = 'i18n_cache_';

interface I18nCacheEntry {
  hash: string;
  data: Record<string, string>;
}

interface PublicI18nResponse {
  unchanged: boolean;
  hash?: string;
  data?: Record<string, string>;
}

/**
 * 从 public API 拉取后端翻译，以 key 为单位覆盖合并到 vue-i18n。
 * 不阻塞 UI：调用方应 fire-and-forget。
 */
export async function fetchBackendI18n(
  i18nInstance: typeof coreI18n,
  locale: SupportedLanguagesType,
): Promise<void> {
  const cacheKey = `${CACHE_PREFIX}${locale}`;

  // 1. 从 localStorage 缓存立即 merge（近零延迟）
  const cached = readCache(cacheKey);
  if (cached) {
    mergeTranslations(i18nInstance, locale, cached.data);
  }

  // 2. 请求后端 public 端点
  try {
    // 默认 responseReturn:'data'：解包 Result.data = { unchanged, hash?, data? }
    const res = await requestClient.get<PublicI18nResponse>(
      `/public/i18n/${encodeURIComponent(locale)}`,
      { params: { hash: cached?.hash ?? '' } },
    );

    if (!res || res.unchanged || !res.data) return;

    // 3. 有新数据：merge + 写缓存
    mergeTranslations(i18nInstance, locale, res.data);
    writeCache(cacheKey, { hash: res.hash ?? '', data: res.data });
  } catch {
    // 静默失败：本地 bundle + localStorage 缓存已兜底
  }
}

/** 将扁平 key 转为嵌套对象后 merge 到 vue-i18n */
function mergeTranslations(
  i18nInstance: typeof coreI18n,
  locale: string,
  kvMap: Record<string, string>,
): void {
  const nested: Record<string, any> = {};

  for (const [key, value] of Object.entries(kvMap)) {
    const parts = key.split('.');
    let target = nested;
    for (let i = 0; i < parts.length - 1; i++) {
      const seg = parts[i];
      if (seg) {
        target[seg] ??= {};
        target = target[seg];
      }
    }
    const last = parts[parts.length - 1];
    if (last) {
      target[last] = value;
    }
  }

  i18nInstance.global.mergeLocaleMessage(locale, nested);
}

function readCache(key: string): I18nCacheEntry | null {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as I18nCacheEntry;
    if (parsed && parsed.hash && parsed.data) return parsed;
    return null;
  } catch {
    return null;
  }
}

function writeCache(key: string, entry: I18nCacheEntry): void {
  try {
    localStorage.setItem(key, JSON.stringify(entry));
  } catch {
    // storage 满或隐私模式
  }
}
