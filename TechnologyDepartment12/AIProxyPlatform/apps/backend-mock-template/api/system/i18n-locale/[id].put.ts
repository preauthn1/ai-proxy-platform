import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import { ensureI18nSeeds, getMockI18nLocaleList, isoNow } from "~/utils/mock-data";
import { pickI18nCamelKeys, toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED_KEYS = ["code", "name", "sort", "remark", "isDefault", "isEnabled"] as const;

const KEY_TO_SNAKE: Record<string, string> = {
  code: "code",
  name: "name",
  sort: "sort",
  remark: "remark",
  isDefault: "is_default",
  isEnabled: "is_enabled",
};

const CODE_PATTERN = /^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$/;

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const list = getMockI18nLocaleList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `i18n-locale ${id} not found`);
  }

  const patch = pickI18nCamelKeys<Record<string, unknown>>(raw, ALLOWED_KEYS);

  if ("name" in patch) {
    const rawName = patch.name;
    const name =
      typeof rawName === "string"
        ? rawName.trim()
        : typeof rawName === "number"
          ? String(rawName).trim()
          : "";
    if (!name) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "name cannot be empty");
    }
    if (name.length > 64) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "name must be ≤ 64 chars");
    }
    patch.name = name;
  }
  if ("code" in patch) {
    const rawCode = patch.code;
    const nextCode =
      typeof rawCode === "string"
        ? rawCode.trim()
        : typeof rawCode === "number"
          ? String(rawCode).trim()
          : "";
    if (!CODE_PATTERN.test(nextCode)) {
      setResponseStatus(event, 400);
      return useResponseError(
        "BadRequest",
        "code must look like a BCP-47 tag (e.g. zh-CN / en-US / ja-JP)",
      );
    }
    const conflict = list.find((x) => x.id !== id && x.deleted_at === 0 && x.code === nextCode);
    if (conflict) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `code ${nextCode} already exists`);
    }
    patch.code = nextCode;
  }
  if ("isEnabled" in patch) {
    const v = Number(patch.isEnabled);
    if (v !== 0 && v !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    patch.isEnabled = v as 0 | 1;
  }
  if ("isDefault" in patch) {
    const v = Number(patch.isDefault);
    if (v !== 0 && v !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isDefault must be 0 or 1");
    }
    patch.isDefault = v as 0 | 1;
  }
  if ("sort" in patch) {
    const v = Number(patch.sort);
    if (!Number.isFinite(v)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "sort must be a number");
    }
    patch.sort = v;
  }

  // isDefault 唯一：若置为 1，先清掉其它的 default
  if (Number(patch.isDefault) === 1) {
    for (let i = 0; i < list.length; i++) {
      if (i !== idx && list[i].deleted_at === 0 && list[i].is_default === 1) {
        list[i] = { ...list[i], is_default: 0, updated_at: isoNow() };
      }
    }
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
  return useResponseSuccess(toI18nCamelRow(list[idx]));
});
