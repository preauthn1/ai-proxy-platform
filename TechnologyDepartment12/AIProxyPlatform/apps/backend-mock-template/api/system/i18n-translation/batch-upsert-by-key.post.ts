import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  isoNow,
  nextI18nId,
} from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * i18n_translation 单 key 多语言事务化 upsert（应用层伪事务）。
 *
 * Body:
 *   translationKey: string                       (必填，原 key)
 *   newTranslationKey?: string                   (可选：改名后的 key；与 translationKey 不同时改名)
 *   items: Array<{localeId, value, isEnabled}>   (必填：要写入的语言行)
 *   deletedIds?: number[]                        (可选：随本次保存一起删除的 row id)
 *
 * 处理顺序（任一阶段失败即返回 errors 并不继续后续阶段）：
 *   1) rename（若 newKey != translationKey）：对 translationKey 下所有未删 row
 *      PUT {translationKey: newKey}，校验新 key 在同 locale 下唯一
 *   2) delete（deletedIds）：软删对应 row
 *   3) upsert（items）：
 *      - 存在 row（deleted_at=0, locale_id, translation_key=translationKey）→ PUT value/isEnabled
 *      - 不存在 → POST {localeId, translationKey, value, isEnabled}
 *
 * 响应：
 *   { ok: true,  affected: { renamed, created, updated, deleted }, values: [...] }
 *   { ok: false, errors: [{code, message, ...}] }
 */

interface UpsertItem {
  localeId?: number;
  value?: string;
  remark?: string;
  isEnabled?: 0 | 1 | boolean;
}

interface RequestBody {
  translationKey?: string;
  newTranslationKey?: string;
  items?: UpsertItem[];
  deletedIds?: Array<number | string>;
}

interface BatchError {
  code: string;
  message: string;
  localeId?: number;
  id?: number;
}

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const raw = ((await readBody(event)) ?? {}) as RequestBody;
  const translationKey = String(raw.translationKey ?? "").trim();
  const newTranslationKey =
    raw.newTranslationKey !== undefined ? String(raw.newTranslationKey).trim() : undefined;
  const items = Array.isArray(raw.items) ? raw.items : [];
  const deletedIdsRaw = Array.isArray(raw.deletedIds) ? raw.deletedIds : [];

  if (!translationKey) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "translationKey is required");
  }
  if (translationKey.length > 255) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "translationKey must be ≤ 255 chars");
  }
  if (
    newTranslationKey !== undefined &&
    (newTranslationKey.length === 0 || newTranslationKey.length > 255)
  ) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "newTranslationKey must be 1..255 chars");
  }

  const list = getMockI18nTranslationList();
  const locales = getMockI18nLocaleList();
  const errors: BatchError[] = [];
  let renamed = 0;
  let created = 0;
  let updated = 0;
  let deleted = 0;

  // === Stage 1: rename ===
  if (newTranslationKey !== undefined && newTranslationKey !== translationKey) {
    const renameTargets = list.filter(
      (x) => x.deleted_at === 0 && x.translation_key === translationKey,
    );
    for (const row of renameTargets) {
      // 校验新 key 在同 locale 下未与其它存活 row 冲突
      const conflict = list.find(
        (x) =>
          x.id !== row.id &&
          x.deleted_at === 0 &&
          x.locale_id === row.locale_id &&
          x.translation_key === newTranslationKey,
      );
      if (conflict) {
        errors.push({
          code: "Conflict",
          message: `translation_key ${newTranslationKey} already exists for locale ${row.locale_id}`,
          localeId: row.locale_id,
        });
        continue;
      }
      const idx = list.indexOf(row);
      list[idx] = {
        ...row,
        translation_key: newTranslationKey,
        updated_at: isoNow(),
        updated_by: 0,
      };
      renamed += 1;
    }
    if (errors.length > 0) {
      setResponseStatus(event, 400);
      return { ok: false, errors };
    }
  }

  // === Stage 2: delete ===
  const deletedIds = deletedIdsRaw.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);

  for (const id of deletedIds) {
    const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
    if (idx < 0) {
      errors.push({
        code: "NotFound",
        message: `i18n-translation ${id} not found`,
        id,
      });
      continue;
    }
    list[idx] = { ...list[idx], deleted_at: Date.now() };
    deleted += 1;
  }
  if (errors.length > 0) {
    setResponseStatus(event, 400);
    return { ok: false, errors };
  }

  // === Stage 3: upsert ===
  // 当前有效 key（rename 后可能已是 newTranslationKey）
  const effectiveKey =
    newTranslationKey !== undefined && newTranslationKey !== translationKey
      ? newTranslationKey
      : translationKey;

  for (const rawItem of items) {
    const localeId = Number(rawItem.localeId);
    if (!Number.isFinite(localeId) || localeId <= 0) {
      errors.push({
        code: "BadRequest",
        message: "localeId is required",
      });
      continue;
    }
    const value = String(rawItem.value ?? "").trim();
    const remark = String(rawItem.remark ?? "").trim();
    if (!value) {
      // 「空白不动」语义：跳过空值
      continue;
    }
    let isEnabled: 0 | 1 = 1;
    if (rawItem.isEnabled !== undefined) {
      const n = Number(rawItem.isEnabled);
      if (n !== 0 && n !== 1) {
        errors.push({
          code: "BadRequest",
          message: "isEnabled must be 0 or 1",
          localeId,
        });
        continue;
      }
      isEnabled = n as 0 | 1;
    }

    const locale = locales.find((l) => l.id === localeId && l.deleted_at === 0);
    if (!locale) {
      errors.push({
        code: "BadRequest",
        message: `locale ${localeId} not found`,
        localeId,
      });
      continue;
    }

    const existingIdx = list.findIndex(
      (x) => x.deleted_at === 0 && x.locale_id === localeId && x.translation_key === effectiveKey,
    );

    if (existingIdx >= 0) {
      list[existingIdx] = {
        ...list[existingIdx],
        value,
        remark,
        is_enabled: isEnabled,
        updated_at: isoNow(),
        updated_by: 0,
      };
      updated += 1;
    } else {
      const now = isoNow();
      const newRow = {
        id: nextI18nId(),
        locale_id: localeId,
        translation_key: effectiveKey,
        value,
        remark,
        is_enabled: isEnabled,
        deleted_at: 0,
        created_at: now,
        updated_at: now,
        created_by: 0,
        updated_by: 0,
      };
      list.unshift(newRow);
      created += 1;
    }
  }
  if (errors.length > 0) {
    setResponseStatus(event, 400);
    return { ok: false, errors };
  }

  // 返回最新该 key 的 values（前端无需再次 GET）
  const refreshedRows = list
    .filter((x) => x.deleted_at === 0 && x.translation_key === effectiveKey)
    .sort((a, b) => a.id - b.id);
  const localeIdToCode = new Map(locales.map((l) => [l.id, l.code] as const));
  const values = refreshedRows.map((row) => ({
    ...toI18nCamelRow(row),
    localeCode: localeIdToCode.get(row.locale_id),
  }));

  return useResponseSuccess({
    ok: true,
    affected: { renamed, created, updated, deleted },
    values,
  });
});
