import { defineEventHandler, readBody, setResponseStatus } from "h3";
import { ensureI18nSeeds, getMockI18nLocaleList, isoNow, nextI18nId } from "~/utils/mock-data";
import { pickI18nCamelKeys, toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const CODE_PATTERN = /^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$/;

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickI18nCamelKeys<{
    code?: string;
    name?: string;
    sort?: number;
    remark?: string;
    isDefault?: 0 | 1 | boolean;
    isEnabled?: 0 | 1 | boolean;
  }>(raw, ["code", "name", "sort", "remark", "isDefault", "isEnabled"]);

  const code = String(body.code ?? "").trim();
  const name = String(body.name ?? "").trim();

  if (!code) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code is required");
  }
  if (!CODE_PATTERN.test(code)) {
    setResponseStatus(event, 400);
    return useResponseError(
      "BadRequest",
      "code must look like a BCP-47 tag (e.g. zh-CN / en-US / ja-JP)",
    );
  }
  if (!name) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name is required");
  }
  if (name.length > 64) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name must be ≤ 64 chars");
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
  let isDefault: 0 | 1 = 0;
  if (body.isDefault !== undefined) {
    const n = Number(body.isDefault);
    if (n !== 0 && n !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isDefault must be 0 or 1");
    }
    isDefault = n as 0 | 1;
  }
  const sort =
    body.sort !== undefined && Number.isFinite(Number(body.sort)) ? Number(body.sort) : 0;

  const list = getMockI18nLocaleList();
  const conflict = list.find((x) => x.deleted_at === 0 && x.code === code);
  if (conflict) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `code ${code} already exists`);
  }

  // isDefault 唯一：写入前把同语言域其它 default 清掉（应用层保证）。
  if (isDefault === 1) {
    for (let i = 0; i < list.length; i++) {
      if (list[i].deleted_at === 0 && list[i].is_default === 1) {
        list[i] = { ...list[i], is_default: 0, updated_at: isoNow() };
      }
    }
  }

  const now = isoNow();
  const newRow = {
    id: nextI18nId(),
    code,
    name,
    is_default: isDefault,
    sort,
    remark: body.remark ?? "",
    is_enabled: isEnabled,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  list.unshift(newRow);
  return useResponseSuccess(toI18nCamelRow(newRow));
});
