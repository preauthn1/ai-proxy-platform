import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  appendMockTaskExecution,
  ensureTemporalTaskSeeds,
  getMockTemporalTaskConfigList,
  isoNow,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 任务配置批量操作。
 *
 * action:
 *   - "enable"  : 批量启用
 *   - "disable" : 批量禁用
 *   - "delete"  : 批量软删（允许存在 execution）
 *   - "trigger" : 批量触发（跳过禁用/不存在；至少触发启用中的）
 *
 * body: { action: "enable"|"disable"|"delete"|"trigger", ids: number[] }
 */
export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const body = (await readBody(event)) as {
    action?: "enable" | "disable" | "delete" | "trigger";
    ids?: number[] | string[];
  };

  const action = body?.action;
  const rawIds = Array.isArray(body?.ids) ? body!.ids : [];
  const ids = rawIds.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);

  if (!action || !["enable", "disable", "delete", "trigger"].includes(action)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "action must be enable|disable|delete|trigger");
  }
  if (ids.length === 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "ids must be a non-empty number[]");
  }

  const list = getMockTemporalTaskConfigList();
  const targets = list.filter((x) => ids.includes(x.id) && x.deleted_at === 0);
  if (targets.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active task-config found for given ids");
  }

  if (action === "delete") {
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

  if (action === "trigger") {
    const enabled = targets.filter((t) => t.is_enabled === 1);
    if (enabled.length === 0) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "no enabled task-config to trigger");
    }
    const executionIds: number[] = [];
    for (const t of enabled) {
      const exec = appendMockTaskExecution(t, { status: "PENDING" });
      executionIds.push(exec.id);
    }
    return useResponseSuccess({
      action,
      affected: enabled.length,
      ids: enabled.map((t) => t.id),
      executionIds,
      skippedDisabled: targets.filter((t) => t.is_enabled === 0).map((t) => t.id),
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
