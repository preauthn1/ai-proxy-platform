import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
} from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 按语言 code 拉启用翻译（下拉用，与 dict-data/by-type 同构）。
 */
export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const code = getRouterParam(event, "code") ?? "";
  const locales = getMockI18nLocaleList();
  const locale = locales.find((x) => x.code === code && x.deleted_at === 0);
  if (!locale) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `i18n-locale ${code} not found`);
  }
  const items = getMockI18nTranslationList().filter(
    (t) => t.locale_id === locale.id && t.deleted_at === 0 && t.is_enabled === 1,
  );
  items.sort((a, b) => a.translation_key.localeCompare(b.translation_key));
  return useResponseSuccess(
    items.map((t) => ({
      ...toI18nCamelRow(t),
      localeCode: locale.code,
    })),
  );
});
