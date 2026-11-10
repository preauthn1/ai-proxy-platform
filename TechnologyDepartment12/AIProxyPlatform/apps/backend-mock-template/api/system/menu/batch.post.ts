import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  clearMenuApis,
  clearRoleMenusByMenuId,
  ensureMenuApiSeeds,
  getMockSysMenuList,
  hasMenuChildren,
  softDeleteMenu,
  updateSysMenu,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 菜单批量操作。
 * action: enable | disable | delete
 * body: { action, ids: number[] }
 * delete 时若有子节点则整批回滚 400。
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

  const list = getMockSysMenuList();
  const targets = list.filter((m) => ids.includes(m.id) && m.deleted_at === 0);
  if (targets.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active menu found for given ids");
  }

  if (action === "delete") {
    // 任一目标有子节点 → 整批回滚
    const blocked = targets.find((t) => hasMenuChildren(t.id));
    if (blocked) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `菜单 ${blocked.name} 仍有子菜单，请先删除`);
    }
    for (const t of targets) {
      clearMenuApis(t.id);
      clearRoleMenusByMenuId(t.id);
      softDeleteMenu(t.id);
    }
    return useResponseSuccess({
      action,
      affected: targets.length,
      ids: targets.map((t) => t.id),
    });
  }

  const isEnabled: 0 | 1 = action === "enable" ? 1 : 0;
  for (const t of targets) {
    updateSysMenu(t.id, { isEnabled });
  }
  return useResponseSuccess({
    action,
    affected: targets.length,
    ids: targets.map((t) => t.id),
  });
});
