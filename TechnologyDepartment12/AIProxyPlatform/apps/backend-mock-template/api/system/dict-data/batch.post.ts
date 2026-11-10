import { defineEventHandler, readBody, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictDataList, isoNow } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 字典项批量操作。
 *
 * action:
 *   - "enable"  : 批量启用（is_enabled = 1）
 *   - "disable" : 批量禁用（is_enabled = 0）
 *   - "delete"  : 批量软删
 *
 * body: { action: "enable" | "disable" | "delete", ids: number[] }
 */
export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  const body = (await readBody(event)) as {
    action?: "enable" | "disable" | "delete";
    ids?: number[] | string[];
  };

  const action = body?.action;
  const rawIds = Array.isArray(body?.ids) ? body!.ids : [];
  const ids = rawIds.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);

  if (!action || !["enable", "disable", "delete"].includes(action)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "action must be enable|disable|delete");
  }
  if (ids.length === 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "ids must be a non-empty number[]");
  }

  const list = getMockDictDataList();
  const targets = list.filter((x) => ids.includes(x.id) && x.deleted_at === 0);
  if (targets.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active dict-data found for given ids");
  }

  if (action === "delete") {
    const nowMs = Date.now();
    for (const d of targets) {
      const idx = list.indexOf(d);
      list[idx] = { ...d, deleted_at: nowMs };
    }
    return useResponseSuccess({
      action,
      affected: targets.length,
      ids: targets.map((d) => d.id),
    });
  }

  const next: 0 | 1 = action === "enable" ? 1 : 0;
  const now = isoNow();
  for (const d of targets) {
    const idx = list.indexOf(d);
    list[idx] = { ...d, is_enabled: next, updated_at: now, updated_by: 0 };
  }
  return useResponseSuccess({
    action,
    affected: targets.length,
    ids: targets.map((d) => d.id),
  });
});
