import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  ensureUserSeeds,
  getMockSysMenuList,
  getMockSysRoleList,
  setRoleMenus,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 全量替换某角色的菜单授权：{ menuIds: number[] }。 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();
  ensureUserSeeds();

  const idStr = getRouterParam(event, "id");
  const roleId = Number(idStr);
  if (!Number.isFinite(roleId)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }
  if (!getMockSysRoleList().some((r) => r.id === roleId && r.deleted_at === 0)) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `role ${roleId} not found`);
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const rawIds = raw.menuIds ?? raw.menu_ids;
  if (!Array.isArray(rawIds)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "menuIds must be an array");
  }
  const menuIds = (rawIds as unknown[]).map((n) => Number(n)).filter((n) => Number.isFinite(n));
  // 校验菜单存在
  const validMenuIds = new Set(
    getMockSysMenuList()
      .filter((m) => m.deleted_at === 0)
      .map((m) => m.id),
  );
  for (const mid of menuIds) {
    if (!validMenuIds.has(mid)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `menu ${mid} not found`);
    }
  }

  setRoleMenus(roleId, menuIds);
  return useResponseSuccess({ roleId, menuIds });
});
