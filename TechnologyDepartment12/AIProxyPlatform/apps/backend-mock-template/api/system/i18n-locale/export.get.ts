import { defineEventHandler, getQuery, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
} from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * GET /api/system/i18n-locale/export?ids=1,2,3&type=raw|simple
 *
 * raw:  { "@type":"raw", locales:[...], translations:[...] }
 * simple: { "@type":"simple", locales: { "zh-CN": { key: value }, ... } }
 */
export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const query = getQuery(event);
  const idsRaw = Array.isArray(query.ids)
    ? query.ids.map((v) => String(v)).join(",")
    : typeof query.ids === "string"
      ? query.ids
      : "";
  const typeRaw = typeof query.type === "string" ? query.type : "";
  const type: "raw" | "simple" = ["raw", "simple"].includes(typeRaw)
    ? (typeRaw as "raw" | "simple")
    : "simple";

  if (!["raw", "simple"].includes(type)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "type must be raw or simple");
  }

  const ids = idsRaw
    .split(",")
    .map((v) => Number(v.trim()))
    .filter((v) => Number.isFinite(v) && v > 0);

  if (ids.length === 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "ids is required");
  }

  const allLocales = getMockI18nLocaleList().filter((x) => x.deleted_at === 0);
  const selectedLocales = allLocales.filter((x) => ids.includes(x.id));

  if (selectedLocales.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active locales found for given ids");
  }

  if (type === "raw") {
    const allTranslations = getMockI18nTranslationList().filter((x) => x.deleted_at === 0);

    const localeIdSet = new Set(selectedLocales.map((l) => l.id));
    const relatedTranslations = allTranslations.filter((t) => localeIdSet.has(t.locale_id));

    // attach localeCode to each translation
    const localeCodeMap = new Map(allLocales.map((l) => [l.id, l.code]));

    return useResponseSuccess({
      "@type": "raw",
      locales: selectedLocales.map(toI18nCamelRow),
      translations: relatedTranslations.map((t) => ({
        ...toI18nCamelRow(t),
        localeCode: localeCodeMap.get(t.locale_id),
      })),
    });
  }

  // simple
  const allTranslations = getMockI18nTranslationList().filter((x) => x.deleted_at === 0);
  const localeCodeMap = new Map(allLocales.map((l) => [l.id, l.code]));

  const locales: Record<string, Record<string, string>> = {};
  for (const l of selectedLocales) {
    locales[l.code] = {};
  }

  const localeIdSet = new Set(selectedLocales.map((l) => l.id));
  for (const t of allTranslations) {
    if (!localeIdSet.has(t.locale_id)) continue;
    const code = localeCodeMap.get(t.locale_id);
    if (!code) continue;
    if (!locales[code]) locales[code] = {};
    locales[code][t.translation_key] = t.value;
  }

  return useResponseSuccess({
    "@type": "simple",
    locales,
  });
});
