import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureDictSeeds,
  getMockDictDataList,
  getMockDictTypeList,
  isAllowedDictDataPlatform,
  isAllowedTagType,
  isoNow,
  nextDictId,
  type DictData,
} from "~/utils/mock-data";
import { pickDictCamelKeys, toDictCamelRow } from "~/utils/dict-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  // 接受 camelCase 字段；同时容忍 snake（迁移期容错）。
  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickDictCamelKeys<{
    typeId?: number | string;
    value?: string;
    label?: string;
    sort?: number;
    isDefault?: boolean | number;
    platform?: string;
    tagType?: string;
    isEnabled?: 0 | 1 | boolean;
    remark?: string;
  }>(raw, [
    "typeId",
    "value",
    "label",
    "sort",
    "isDefault",
    "platform",
    "tagType",
    "isEnabled",
    "remark",
  ]);

  const typeId = Number(body.typeId);
  const value = String(body.value ?? "").trim();
  const label = String(body.label ?? "").trim();

  if (!Number.isFinite(typeId) || typeId <= 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "typeId is required");
  }
  const parent = getMockDictTypeList().find((t) => t.id === typeId && t.deleted_at === 0);
  if (!parent) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `dict-type ${typeId} not found or deleted`);
  }
  if (!value) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "value is required");
  }
  if (value.length > 64) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "value must be ≤ 64 chars");
  }
  if (!label) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "label is required");
  }
  if (label.length > 128) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "label must be ≤ 128 chars");
  }

  // platform 校验：未传则默认 general；传了则必须在枚举内
  const rawPlatform = body.platform;
  let platform: DictData["platform"] | null = null;
  if (isAllowedDictDataPlatform(rawPlatform)) {
    platform = rawPlatform;
  } else if (rawPlatform === undefined) {
    platform = "general";
  }
  if (platform === null) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `platform must be one of general|react-admin|vue-admin`);
  }

  // tagType 校验：未传则默认 default；传了则必须在枚举内
  const rawTagType = body.tagType;
  let tagType: DictData["tag_type"] | null = null;
  if (isAllowedTagType(rawTagType)) {
    tagType = rawTagType;
  } else if (rawTagType === undefined) {
    tagType = "default";
  }
  if (tagType === null) {
    setResponseStatus(event, 400);
    return useResponseError(
      "BadRequest",
      `tagType must be one of ${[
        "default",
        "primary",
        "success",
        "warning",
        "error",
        "processing",
        "magenta",
        "red",
        "volcano",
        "orange",
        "gold",
        "lime",
        "green",
        "cyan",
        "blue",
        "geekblue",
        "purple",
      ].join("|")}`,
    );
  }

  // isEnabled 归一为 0|1
  let isEnabled: 0 | 1 = 1;
  if (body.isEnabled !== undefined) {
    const n = Number(body.isEnabled);
    if (n !== 0 && n !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    isEnabled = n as 0 | 1;
  }

  const list = getMockDictDataList();
  // 唯一键与 schema 对齐：(type_id, value, platform, deleted_at)
  // 同 type 下不同 platform 允许同名 value
  const conflict = list.find(
    (x) =>
      x.deleted_at === 0 && x.type_id === typeId && x.value === value && x.platform === platform,
  );
  if (conflict) {
    setResponseStatus(event, 400);
    return useResponseError(
      "BadRequest",
      `value ${value} already exists in type ${typeId} platform ${platform}`,
    );
  }

  const now = isoNow();
  const newRow: DictData = {
    id: nextDictId(),
    type_id: typeId,
    value,
    label,
    sort: Number(body.sort ?? 0),
    is_default: (body.isDefault ? 1 : 0) as 0 | 1,
    platform,
    tag_type: tagType,
    is_enabled: isEnabled,
    deleted_at: 0,
    remark: body.remark ?? "",
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  list.unshift(newRow);
  return useResponseSuccess(toDictCamelRow(newRow));
});
