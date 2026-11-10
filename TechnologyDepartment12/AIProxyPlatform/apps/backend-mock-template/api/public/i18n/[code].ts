import { defineEventHandler, getRouterParam, getQuery, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
} from "~/utils/mock-data";
import { computeI18nHash } from "~/utils/i18n-hash";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * GET /api/public/i18n/:code?hash=<hash>
 *
 * Public 端点：前端无需登录态即可拉取翻译数据。与 java-admin Result 对齐：
 * - 成功：{ code:0, msg:'ok', data: { unchanged, hash?, data? } }
 * - 前端带上上次缓存的 hash → data.unchanged=true
 * - 无 hash 或有变化 → data.unchanged=false + hash + data(KV)
 */
export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const code = getRouterParam(event, "code") ?? "";
  const clientHash = getQuery(event).hash as string | undefined;

  const locales = getMockI18nLocaleList();
  const locale = locales.find((x) => x.code === code && x.deleted_at === 0);
  if (!locale) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `i18n-locale ${code} not found`);
  }

  const items = getMockI18nTranslationList().filter(
    (t) => t.locale_id === locale.id && t.deleted_at === 0 && t.is_enabled === 1,
  );

  // 构建 key-value map
  const data: Record<string, string> = {};
  for (const item of items) {
    data[item.translation_key] = item.value;
  }

  const serverHash = computeI18nHash(data);

  // hash 一致 → 无变更
  if (clientHash && clientHash === serverHash) {
    return useResponseSuccess({ unchanged: true });
  }

  return useResponseSuccess({ unchanged: false, hash: serverHash, data });
});
