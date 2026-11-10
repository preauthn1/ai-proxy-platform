import { defineEventHandler, getQuery } from "h3";
import { ensureI18nSeeds, getMockI18nLocaleList, type I18nLocale } from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const query = getQuery(event);
  const code = (query.code ?? query["code[]"]) as string | string[] | undefined;
  const { status, name } = query;
  let items: I18nLocale[] = getMockI18nLocaleList().filter((x) => x.deleted_at === 0);

  if (Array.isArray(code)) {
    const codes = new Set((code as unknown[]).map((v) => String(v)));
    if (codes.size > 0) {
      items = items.filter((x) => codes.has(x.code));
    }
  } else if (code) {
    const q = String(code as string).toLowerCase();
    items = items.filter((x) => x.code.toLowerCase().includes(q));
  }
  if (name) {
    const q = String(name as string);
    items = items.filter((x) => x.name.includes(q));
  }
  if (["0", "1"].includes(status as string)) {
    items = items.filter((x) => x.is_enabled === Number(status));
  }
  items.sort((a, b) => a.sort - b.sort || a.id - b.id);
  return useResponseSuccess(items.map(toI18nCamelRow));
});
