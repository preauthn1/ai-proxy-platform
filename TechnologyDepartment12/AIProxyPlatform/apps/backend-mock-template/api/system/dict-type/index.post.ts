import { defineEventHandler, readBody, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictTypeList, isoNow, nextDictId } from "~/utils/mock-data";
import { pickDictCamelKeys, toDictCamelRow } from "~/utils/dict-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  // 接受 camelCase 字段；同时容忍 snake（迁移期容错）。
  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickDictCamelKeys<{
    code?: string;
    name?: string;
    remark?: string;
    isEnabled?: 0 | 1 | boolean;
  }>(raw, ["code", "name", "remark", "isEnabled"]);

  const code = String(body.code ?? "").trim();
  const name = String(body.name ?? "").trim();

  if (!code) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code is required");
  }
  if (!CODE_PATTERN.test(code)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code must match ^[a-z][a-z0-9_]{0,63}$");
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

  const list = getMockDictTypeList();
  const conflict = list.find((x) => x.deleted_at === 0 && x.code === code);
  if (conflict) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `code ${code} already exists`);
  }

  const now = isoNow();
  const newRow = {
    id: nextDictId(),
    code,
    name,
    remark: body.remark ?? "",
    is_enabled: isEnabled,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  list.unshift(newRow);
  return useResponseSuccess(toDictCamelRow(newRow));
});
