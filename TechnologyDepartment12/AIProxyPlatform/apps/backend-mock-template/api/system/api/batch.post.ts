import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  clearApiMenus,
  clearRoleApisByApiId,
  ensureMenuApiSeeds,
  getMockSysApiList,
  softDeleteApi,
  updateSysApi,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 接口批量操作。
 * action: enable | disable | delete
 * body: { action, ids: number[] }
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

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

  const list = getMockSysApiList();
  const targets = list.filter((a) => ids.includes(a.id) && a.deleted_at === 0);
  if (targets.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active api found for given ids");
  }

  if (action === "delete") {
    for (const t of targets) {
      clearApiMenus(t.id);
      clearRoleApisByApiId(t.id);
      softDeleteApi(t.id);
    }
    return useResponseSuccess({
      action,
      affected: targets.length,
      ids: targets.map((t) => t.id),
    });
  }

  const isEnabled: 0 | 1 = action === "enable" ? 1 : 0;
  for (const t of targets) {
    updateSysApi(t.id, { isEnabled });
  }
  return useResponseSuccess({
    action,
    affected: targets.length,
    ids: targets.map((t) => t.id),
  });
});
