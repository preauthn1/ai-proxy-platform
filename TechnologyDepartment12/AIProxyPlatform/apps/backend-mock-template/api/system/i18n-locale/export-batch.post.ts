import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
} from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * POST /api/system/i18n-locale/export-batch
 *
 * Body: { ids: number[], format: 'raw' | 'simple' }
 *
 * 响应：
 *   {
 *     files: Array<{
 *       code: string,
 *       format: 'raw' | 'simple',
 *       content: RawExport | SimpleExport,
 *     }>,
 *   }
 *
 * 行为：
 *   - 每个启用 locale 生成一个文件。
 *   - raw 文件：`{@type:'raw', locale:{...}, translations:[{id,translationKey,value,remark,isEnabled}]}`
 *     （不带 localeCode，依赖文件级 locale）。
 *   - simple 文件：`{@type:'simple', <nested kv>}`（顶层为翻译键字典，无 prefix）。
 */

interface BatchRequest {
  ids?: number[];
  format?: "raw" | "simple";
}

function flattenToDict(entries: Array<{ key: string; value: string }>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const { key, value } of entries) {
    const parts = key.split(".");
    let cur: Record<string, unknown> = out;
    for (let i = 0; i < parts.length - 1; i++) {
      const p = parts[i];
      if (typeof cur[p] !== "object" || cur[p] === null || Array.isArray(cur[p])) {
        cur[p] = {};
      }
      cur = cur[p] as Record<string, unknown>;
    }
    cur[parts[parts.length - 1]] = value;
  }
  return out;
}

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const body = (await readBody(event)) as BatchRequest | undefined;
  if (!body || !Array.isArray(body.ids)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "ids array is required");
  }
  const format: "raw" | "simple" = body.format === "raw" ? "raw" : "simple";

  const ids = body.ids.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);

  const allLocales = getMockI18nLocaleList().filter((x) => x.deleted_at === 0);
  const selectedLocales = allLocales.filter((x) => ids.includes(x.id));

  if (selectedLocales.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active locales found for given ids");
  }

  const allTranslations = getMockI18nTranslationList().filter((x) => x.deleted_at === 0);

  const files = selectedLocales.map((locale) => {
    const code = locale.code;
    const localeId = locale.id;
    const myTranslations = allTranslations
      .filter((t) => t.locale_id === localeId)
      .map((t) => ({
        id: t.id,
        translationKey: t.translation_key,
        value: t.value,
        remark: t.remark,
        isEnabled: t.is_enabled,
      }));

    if (format === "raw") {
      return {
        code,
        format: "raw" as const,
        content: {
          "@type": "raw",
          locale: toI18nCamelRow(locale),
          translations: myTranslations,
        },
      };
    }
    // simple: 嵌套字典
    const flat = myTranslations.map((t) => ({
      key: t.translationKey,
      value: t.value,
    }));
    return {
      code,
      format: "simple" as const,
      content: {
        "@type": "simple",
        ...flattenToDict(flat),
      },
    };
  });

  return useResponseSuccess({ files });
});
