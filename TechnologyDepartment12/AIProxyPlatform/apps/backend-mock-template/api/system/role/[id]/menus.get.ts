import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  ensureUserSeeds,
  getMockSysMenuList,
  getRoleMenuIds,
} from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 读取某角色可授权的菜单列表（带 bound 标记）。
 * 复用 /system/menu/all 的菜单数据，按 role_menu 标记 bound。
 */
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

  const boundIds = new Set(getRoleMenuIds(roleId));
  const items = getMockSysMenuList()
    .filter((m) => m.deleted_at === 0)
    .sort((a, b) => a.sort - b.sort || a.id - b.id)
    .map((m) => ({ ...toCamelRow(m), bound: boundIds.has(m.id) }));

  return useResponseSuccess(items);
});
