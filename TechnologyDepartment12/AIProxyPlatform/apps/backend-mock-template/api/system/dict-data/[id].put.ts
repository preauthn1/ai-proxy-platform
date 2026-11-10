import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureDictSeeds,
  getMockDictDataList,
  isAllowedDictDataPlatform,
  isAllowedTagType,
  isoNow,
} from "~/utils/mock-data";
import { pickDictCamelKeys, toDictCamelRow } from "~/utils/dict-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED_KEYS = [
  "value",
  "label",
  "sort",
  "isDefault",
  "platform",
  "tagType",
  "isEnabled",
  "remark",
] as const;

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
  const list = getMockDictDataList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `dict-data ${id} not found`);
  }

  const patch = pickDictCamelKeys<Record<string, unknown>>(raw, ALLOWED_KEYS);

  if ("value" in patch) {
    const rawValue = patch.value;
    const v =
      typeof rawValue === "string"
        ? rawValue.trim()
        : typeof rawValue === "number"
          ? String(rawValue).trim()
          : "";
    if (!v) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "value cannot be empty");
    }
    if (v.length > 64) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "value must be ≤ 64 chars");
    }
    patch.value = v;
  }
  if ("label" in patch) {
    const rawLabel = patch.label;
    const l =
      typeof rawLabel === "string"
        ? rawLabel.trim()
        : typeof rawLabel === "number"
          ? String(rawLabel).trim()
          : "";
    if (!l) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "label cannot be empty");
    }
    if (l.length > 128) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "label must be ≤ 128 chars");
    }
    patch.label = l;
  }
  if ("sort" in patch) {
    const s = Number(patch.sort);
    if (!Number.isFinite(s)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "sort must be a number");
    }
    patch.sort = s;
  }
  if ("isDefault" in patch) {
    patch.isDefault = patch.isDefault ? 1 : 0;
  }
  if ("platform" in patch) {
    if (!isAllowedDictDataPlatform(patch.platform)) {
      setResponseStatus(event, 400);
      return useResponseError(
        "BadRequest",
        "platform must be one of general|react-admin|vue-admin",
      );
    }
  }

  // 唯一性：(type_id, value, platform) 三元组；value/platform 任一变更都要重检
  {
    const nextValue = ("value" in patch ? (patch.value as string) : list[idx].value) as string;
    const nextPlatform = (
      "platform" in patch ? (patch.platform as string) : list[idx].platform
    ) as string;
    const conflict = list.find(
      (x) =>
        x.id !== id &&
        x.deleted_at === 0 &&
        x.type_id === list[idx].type_id &&
        x.value === nextValue &&
        x.platform === nextPlatform,
    );
    if (conflict) {
      setResponseStatus(event, 400);
      return useResponseError(
        "BadRequest",
        `value ${nextValue} already exists in type ${list[idx].type_id} platform ${nextPlatform}`,
      );
    }
  }
  if ("tagType" in patch) {
    if (!isAllowedTagType(patch.tagType)) {
      setResponseStatus(event, 400);
      return useResponseError(
        "BadRequest",
        "tagType must be one of default|primary|success|warning|error|processing|magenta|red|volcano|orange|gold|lime|green|cyan|blue|geekblue|purple",
      );
    }
  }
  if ("isEnabled" in patch) {
    const v = Number(patch.isEnabled);
    if (v !== 0 && v !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    patch.isEnabled = v as 0 | 1;
  }

  // 写入仍使用 snake；按 ALLOWED_KEYS 映射回 snake key 避免内存行混入 camel 字段。
  const snakePatch: Record<string, unknown> = {};
  const KEY_TO_SNAKE: Record<string, string> = {
    value: "value",
    label: "label",
    sort: "sort",
    isDefault: "is_default",
    platform: "platform",
    tagType: "tag_type",
    isEnabled: "is_enabled",
    remark: "remark",
  };
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
