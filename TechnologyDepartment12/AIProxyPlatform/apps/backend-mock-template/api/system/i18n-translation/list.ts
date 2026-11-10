import { defineEventHandler, getQuery } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  type I18nTranslation,
} from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { usePageResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const query = getQuery(event);
  const { page = 1, pageSize = 20, localeId, localeCode, value, status, byKey } = query;

  const shared = getMockI18nTranslationList();
  const locales = getMockI18nLocaleList();
  const localeCodeToId = new Map(
    locales.filter((l) => l.deleted_at === 0).map((l) => [l.code, l.id] as const),
  );
  const localeIdToCode = new Map(
    locales.filter((l) => l.deleted_at === 0).map((l) => [l.id, l.code] as const),
  );

  let filtered: I18nTranslation[] = shared.filter((x) => x.deleted_at === 0);
  if (localeId !== undefined && localeId !== "") {
    const id = Number(localeId);
    if (Number.isFinite(id)) {
      filtered = filtered.filter((x) => x.locale_id === id);
    }
  } else if (localeCode && typeof localeCode === "string") {
    const id = localeCodeToId.get(String(localeCode));
    if (id !== undefined) {
      filtered = filtered.filter((x) => x.locale_id === id);
    } else {
      filtered = [];
    }
  }
  if (value) {
    const q = String(value as string);
    filtered = filtered.filter(
      (x) =>
        x.translation_key.toLowerCase().includes(q.toLowerCase()) ||
        x.value.toLowerCase().includes(q.toLowerCase()),
    );
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((x) => x.is_enabled === Number(status));
  }
  filtered.sort((a, b) => a.id - b.id);

  if (byKey === "true" || byKey === "1") {
    // ponytail: 按 translationKey 内存分组；mock 数据量小，无需 SQL。
    const byKeyMap = new Map<
      string,
      {
        translationKey: string;
        localeCount: number;
        sampleRowId: number;
        sampleLocaleId: number;
        sampleLocaleCode?: string;
        sampleUpdatedAt: string;
      }
    >();
    for (const row of filtered) {
      const existing = byKeyMap.get(row.translation_key);
      if (!existing) {
        byKeyMap.set(row.translation_key, {
          translationKey: row.translation_key,
          localeCount: 1,
          sampleRowId: row.id,
          sampleLocaleId: row.locale_id,
          sampleLocaleCode: localeIdToCode.get(row.locale_id),
          sampleUpdatedAt: row.updated_at,
        });
      } else {
        existing.localeCount += 1;
        if (row.updated_at > existing.sampleUpdatedAt) {
          existing.sampleUpdatedAt = row.updated_at;
        }
      }
    }
    const rows = [...byKeyMap.values()].map((r) => toI18nCamelRow(r));
    return usePageResponseSuccess(page as string, pageSize as string, rows);
  }

  // 注入 localeCode 给前端
  const rows = filtered.map((row) => {
    const code = locales.find((l) => l.id === row.locale_id)?.code;
    return toI18nCamelRow({ ...row, localeCode: code ?? row.localeCode });
  });

  return usePageResponseSuccess(page as string, pageSize as string, rows);
});
