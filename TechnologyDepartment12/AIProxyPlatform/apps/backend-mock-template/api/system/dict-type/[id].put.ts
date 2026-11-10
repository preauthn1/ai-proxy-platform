import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictTypeList, isoNow } from "~/utils/mock-data";
import { pickDictCamelKeys, toDictCamelRow } from "~/utils/dict-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED_KEYS = ["code", "name", "remark", "isEnabled"] as const;

const KEY_TO_SNAKE: Record<string, string> = {
  code: "code",
  name: "name",
  remark: "remark",
  isEnabled: "is_enabled",
};

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  // 接受 camelCase 字段；同时容忍 snake（迁移期容错）。
  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const list = getMockDictTypeList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `dict-type ${id} not found`);
  }

  const patch = pickDictCamelKeys<Record<string, unknown>>(raw, ALLOWED_KEYS);

  // name 校验
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
  // isEnabled 校验
  if ("isEnabled" in patch) {
    const v = Number(patch.isEnabled);
    if (v !== 0 && v !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    patch.isEnabled = v as 0 | 1;
  }

  // code 唯一性校验
  if ("code" in patch) {
    const nextCode = (patch.code as string | undefined) ?? list[idx].code;
    const conflict = list.find((x) => x.id !== id && x.deleted_at === 0 && x.code === nextCode);
    if (conflict) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `code ${nextCode} already exists`);
    }
  }

  // 写入仍用 snake；按 ALLOWED_KEYS 映射回 snake key
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
  return useResponseSuccess(toDictCamelRow(list[idx]));
});
