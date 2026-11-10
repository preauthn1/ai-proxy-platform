import { defineEventHandler, getRouterParam } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  type I18nTranslation,
} from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseSuccess } from "~/utils/response";

/**
 * 按 translation_key 聚合该 key 在所有启用语种下的版本。
 * 缺失 key 时返回空 values（前端按新建路径渲染空表单）。
 */
export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const rawKey = getRouterParam(event, "key") ?? "";
  const translationKey = String(rawKey).trim();
  if (!translationKey) {
    // 空 key：按「无匹配」处理，返回空 values
    return useResponseSuccess({ translationKey: "", values: [] });
  }

  const list = getMockI18nTranslationList();
  const locales = getMockI18nLocaleList();
  const localeIdToCode = new Map(
    locales.filter((l) => l.deleted_at === 0).map((l) => [l.id, l.code] as const),
  );

  const rows: I18nTranslation[] = list.filter(
    (x) => x.deleted_at === 0 && x.translation_key === translationKey,
  );
  rows.sort((a, b) => a.id - b.id);

  const values = rows.map((row) => ({
    ...toI18nCamelRow(row),
    localeCode: localeIdToCode.get(row.locale_id),
  }));

  return useResponseSuccess({ translationKey, values });
});
