import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  isoNow,
} from "~/utils/mock-data";
import { pickI18nCamelKeys, toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED_KEYS = ["translationKey", "value", "remark", "isEnabled"] as const;

const KEY_TO_SNAKE: Record<string, string> = {
  translationKey: "translation_key",
  value: "value",
  remark: "remark",
  isEnabled: "is_enabled",
};

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const list = getMockI18nTranslationList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `i18n-translation ${id} not found`);
  }

  const patch = pickI18nCamelKeys<Record<string, unknown>>(raw, ALLOWED_KEYS);

  if ("translationKey" in patch) {
    const rawKey = patch.translationKey;
    const next =
      typeof rawKey === "string"
        ? rawKey.trim()
        : typeof rawKey === "number"
          ? String(rawKey).trim()
          : "";
    if (!next) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "translationKey cannot be empty");
    }
    if (next.length > 255) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "translationKey must be ≤ 255 chars");
    }
    const conflict = list.find(
      (x) =>
        x.id !== id &&
        x.deleted_at === 0 &&
        x.locale_id === list[idx].locale_id &&
        x.translation_key === next,
    );
    if (conflict) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `translation_key ${next} already exists`);
    }
    patch.translationKey = next;
  }
  if ("value" in patch) {
    const rawVal = patch.value;
    const v =
      typeof rawVal === "string"
        ? rawVal.trim()
        : typeof rawVal === "number"
          ? String(rawVal).trim()
          : "";
    if (!v) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "value cannot be empty");
    }
    patch.value = v;
  }
  if ("isEnabled" in patch) {
    const n = Number(patch.isEnabled);
    if (n !== 0 && n !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    patch.isEnabled = n as 0 | 1;
  }

  const snakePatch: Record<string, unknown> = {};
  for (const k of ALLOWED_KEYS) {
    if (k in patch) snakePatch[KEY_TO_SNAKE[k]] = patch[k];
  }

  list[idx] = {
    ...list[idx],
    ...snakePatch,
    updated_at: isoNow(),
    updated_by: 0,
  };
  const locale = getMockI18nLocaleList().find((l) => l.id === list[idx].locale_id);
  return useResponseSuccess(
    toI18nCamelRow({ ...list[idx], localeCode: locale?.code ?? list[idx].localeCode }),
  );
});
