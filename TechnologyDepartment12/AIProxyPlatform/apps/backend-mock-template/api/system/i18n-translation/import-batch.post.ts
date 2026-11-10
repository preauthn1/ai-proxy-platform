import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  isoNow,
  nextI18nId,
  type I18nLocale,
  type I18nTranslation,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * POST /api/system/i18n-translation/import-batch
 *
 * Body: {
 *   items: [
 *     {
 *       name: string,
 *       prefix?: string,
 *       localeCode: string,
 *       format: 'raw' | 'simple',
 *       payload: any,
 *     },
 *   ],
 * }
 *
 * 语义：
 *   - 每个文件一个逻辑事务（try/catch 隔离）：单个文件失败不影响其他文件。
 *   - raw 文件：upsert locale 元数据 + 按 prefix/localeCode 拼接后 upsert translations。
 *   - simple 文件：unflatten 嵌套字典 + prefix 拼接 + upsert translations。
 *   - 重复 key（同 localeCode + translationKey）静默合并：后到者覆盖前者（前端预览已标记）。
 *
 * 响应：
 *   {
 *     ok: boolean,
 *     affected: {
 *       createdLocales: number,
 *       softDeleted: number,
 *       createdTranslations: number,
 *       perFile: Array<{ name, ok, error?, createdLocales, softDeleted, createdTranslations }>,
 *     },
 *   }
 */

interface BatchItem {
  name?: string;
  prefix?: string;
  localeCode?: string;
  format?: "raw" | "simple";
  payload?: unknown;
}

interface BatchRequest {
  items?: BatchItem[];
}

interface PerFileResult {
  name: string;
  ok: boolean;
  error?: string;
  createdLocales: number;
  softDeleted: number;
  createdTranslations: number;
}

interface RawTranslationRow {
  id?: number;
  translationKey: string;
  value: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

interface RawPayload {
  "@type"?: "raw";
  locale?: {
    code: string;
    name: string;
    isDefault?: 0 | 1;
    sort?: number;
    remark?: string;
    isEnabled?: 0 | 1;
  };
  translations?: RawTranslationRow[];
}

function unflatten(
  obj: Record<string, unknown>,
  prefix = "",
): Array<{ key: string; value: string }> {
  const out: Array<{ key: string; value: string }> = [];
  for (const [k, v] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === "object" && !Array.isArray(v)) {
      out.push(...unflatten(v as Record<string, unknown>, next));
    } else if (typeof v === "string") {
      out.push({ key: next, value: v });
    }
  }
  return out;
}

function ensureLocale(
  localeList: I18nLocale[],
  code: string,
  meta?: RawPayload["locale"],
): { id: number; created: boolean } {
  const existing = localeList.find((l) => l.code === code && l.deleted_at === 0);
  if (existing) {
    if (meta) {
      existing.name = meta.name;
      existing.is_default = meta.isDefault ?? existing.is_default;
      existing.sort = meta.sort ?? existing.sort;
      existing.remark = meta.remark ?? existing.remark;
      existing.is_enabled = meta.isEnabled ?? existing.is_enabled;
      existing.updated_at = isoNow();
    }
    return { id: existing.id, created: false };
  }
  const id = nextI18nId();
  const now = isoNow();
  localeList.push({
    id,
    code,
    name: meta?.name ?? code,
    is_default: meta?.isDefault ?? 0,
    sort: meta?.sort ?? 0,
    remark: meta?.remark ?? "",
    is_enabled: meta?.isEnabled ?? 1,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  });
  return { id, created: true };
}

function upsertTranslation(
  translationList: I18nTranslation[],
  localeId: number,
  translationKey: string,
  value: string,
  remark: string,
  isEnabled: 0 | 1,
  nowMs: number,
): { softDeleted: number; created: number } {
  let softDeleted = 0;
  for (let i = 0; i < translationList.length; i++) {
    const existing = translationList[i];
    if (
      existing.deleted_at === 0 &&
      existing.locale_id === localeId &&
      existing.translation_key === translationKey
    ) {
      translationList[i] = { ...existing, deleted_at: nowMs };
      softDeleted++;
    }
  }
  const now = isoNow();
  translationList.unshift({
    id: nextI18nId(),
    locale_id: localeId,
    translation_key: translationKey,
    value,
    remark,
    is_enabled: isEnabled,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  });
  return { softDeleted, created: 1 };
}

function processOne(
  item: BatchItem,
  localeList: I18nLocale[],
  translationList: I18nTranslation[],
  nowMs: number,
): PerFileResult {
  const name = item.name ?? "(unnamed)";
  try {
    if (!item.localeCode || typeof item.localeCode !== "string") {
      throw new Error("缺少 localeCode");
    }
    if (item.format !== "raw" && item.format !== "simple") {
      throw new Error("format 必须是 raw 或 simple");
    }
    if (item.payload === undefined || item.payload === null) {
      throw new Error("缺少 payload");
    }
    const prefix = (item.prefix ?? "").replace(/^\.+|\.+$/g, "");

    let createdLocales = 0;
    let softDeleted = 0;
    let createdTranslations = 0;

    if (item.format === "raw") {
      const payload = item.payload as RawPayload;
      // 1) upsert locale
      const localeMeta = payload.locale ?? {
        code: item.localeCode,
        name: item.localeCode,
      };
      const { created: localeCreated } = ensureLocale(localeList, item.localeCode, {
        ...localeMeta,
        code: item.localeCode,
      });
      if (localeCreated) createdLocales++;
      const localeRow = localeList.find((l) => l.code === item.localeCode && l.deleted_at === 0);
      if (!localeRow) throw new Error("locale 创建失败");
      // 2) upsert translations
      if (Array.isArray(payload.translations)) {
        for (const t of payload.translations) {
          if (!t.translationKey || typeof t.value !== "string") continue;
          const finalKey = prefix ? `${prefix}.${t.translationKey}` : t.translationKey;
          const { softDeleted: sd, created } = upsertTranslation(
            translationList,
            localeRow.id,
            finalKey,
            t.value,
            t.remark ?? "",
            t.isEnabled ?? 1,
            nowMs,
          );
          softDeleted += sd;
          createdTranslations += created;
        }
      }
    } else {
      // simple
      const payload = item.payload as Record<string, unknown>;
      // 跳过 '@type' 字段
      const { ["@type"]: _t, ...rest } = payload;
      const flat = unflatten(rest as Record<string, unknown>);
      // simple 文件不携带 locale 元数据；如果语言不存在则惰性创建
      const localeRow = localeList.find((l) => l.code === item.localeCode && l.deleted_at === 0);
      let localeId: number;
      if (localeRow) {
        localeId = localeRow.id;
      } else {
        const { id, created } = ensureLocale(localeList, item.localeCode);
        localeId = id;
        if (created) createdLocales++;
      }
      for (const { key, value } of flat) {
        const finalKey = prefix ? `${prefix}.${key}` : key;
        const { softDeleted: sd, created } = upsertTranslation(
          translationList,
          localeId,
          finalKey,
          value,
          "",
          1,
          nowMs,
        );
        softDeleted += sd;
        createdTranslations += created;
      }
    }

    return {
      name,
      ok: true,
      createdLocales,
      softDeleted,
      createdTranslations,
    };
  } catch (err) {
    return {
      name,
      ok: false,
      error: (err as Error).message ?? String(err),
      createdLocales: 0,
      softDeleted: 0,
      createdTranslations: 0,
    };
  }
}

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const body = (await readBody(event)) as BatchRequest | undefined;
  if (!body || !Array.isArray(body.items)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "items array is required");
  }

  const localeList = getMockI18nLocaleList();
  const translationList = getMockI18nTranslationList();
  const nowMs = Date.now();

  let totalCreatedLocales = 0;
  let totalSoftDeleted = 0;
  let totalCreatedTranslations = 0;
  const perFile: PerFileResult[] = [];

  for (const item of body.items) {
    const r = processOne(item, localeList, translationList, nowMs);
    perFile.push(r);
    totalCreatedLocales += r.createdLocales;
    totalSoftDeleted += r.softDeleted;
    totalCreatedTranslations += r.createdTranslations;
  }

  const ok = perFile.every((p) => p.ok);

  return useResponseSuccess({
    ok,
    affected: {
      createdLocales: totalCreatedLocales,
      softDeleted: totalSoftDeleted,
      createdTranslations: totalCreatedTranslations,
      perFile,
    },
  });
});
