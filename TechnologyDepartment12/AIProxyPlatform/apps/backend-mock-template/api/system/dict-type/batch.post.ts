import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureDictSeeds,
  getMockDictDataList,
  getMockDictTypeList,
  isoNow,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 字典类型批量操作。
 *
 * action:
 *   - "enable"  : 批量启用（is_enabled = 1）
 *   - "disable" : 批量禁用（is_enabled = 0）
 *   - "delete"  : 批量软删；若仍有字典项，整个事务回滚并返回 400。
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

  const list = getMockDictTypeList();
  const targets = list.filter((x) => ids.includes(x.id) && x.deleted_at === 0);
  if (targets.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active dict-type found for given ids");
  }

  if (action === "delete") {
    // 若仍有字典项，禁止删除
    const entries = getMockDictDataList();
    const blocked = targets.find((t) =>
      entries.some((d) => d.type_id === t.id && d.deleted_at === 0),
    );
    if (blocked) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `字典类型 ${blocked.code} 仍有字典项，请先清空`);
    }
    const nowMs = Date.now();
    for (const t of targets) {
      const idx = list.indexOf(t);
      list[idx] = { ...t, deleted_at: nowMs };
    }
    return useResponseSuccess({
      action,
      affected: targets.length,
      ids: targets.map((t) => t.id),
    });
  }

  // enable / disable
  const next: 0 | 1 = action === "enable" ? 1 : 0;
  const now = isoNow();
  for (const t of targets) {
    const idx = list.indexOf(t);
    list[idx] = { ...t, is_enabled: next, updated_at: now, updated_by: 0 };
  }
  return useResponseSuccess({
    action,
    affected: targets.length,
    ids: targets.map((t) => t.id),
  });
});
