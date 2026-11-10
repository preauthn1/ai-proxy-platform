import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  isoNow,
  nextI18nId,
} from "~/utils/mock-data";
import { pickI18nCamelKeys, toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickI18nCamelKeys<{
    localeId?: number;
    translationKey?: string;
    value?: string;
    remark?: string;
    isEnabled?: 0 | 1 | boolean;
  }>(raw, ["localeId", "translationKey", "value", "remark", "isEnabled"]);

  const localeId = Number(body.localeId);
  const translationKey = String(body.translationKey ?? "").trim();
  const value = String(body.value ?? "").trim();

  if (!Number.isFinite(localeId) || localeId <= 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "localeId is required");
  }
  if (!translationKey) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "translationKey is required");
  }
  if (translationKey.length > 255) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "translationKey must be ≤ 255 chars");
  }
  if (!value) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "value is required");
  }

  let isEnabled: 0 | 1 = 1;
  if (body.isEnabled !== undefined) {
    const n = Number(body.isEnabled);
    if (n !== 0 && n !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    isEnabled = n as 0 | 1;
  }

  const locales = getMockI18nLocaleList();
  const locale = locales.find((l) => l.id === localeId && l.deleted_at === 0);
  if (!locale) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `locale ${localeId} not found`);
  }

  const list = getMockI18nTranslationList();
  const conflict = list.find(
    (x) => x.deleted_at === 0 && x.locale_id === localeId && x.translation_key === translationKey,
  );
  if (conflict) {
    setResponseStatus(event, 400);
    return useResponseError(
      "BadRequest",
      `translation_key ${translationKey} already exists for ${locale.code}`,
    );
  }

  const now = isoNow();
  const newRow = {
    id: nextI18nId(),
    locale_id: localeId,
    translation_key: translationKey,
    value,
    remark: body.remark ?? "",
    is_enabled: isEnabled,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  list.unshift(newRow);
  return useResponseSuccess(toI18nCamelRow({ ...newRow, localeCode: locale.code }));
});
