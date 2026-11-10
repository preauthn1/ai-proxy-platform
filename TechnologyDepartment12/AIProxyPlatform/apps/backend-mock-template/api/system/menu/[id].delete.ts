import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  clearMenuApis,
  clearRoleMenusByMenuId,
  ensureMenuApiSeeds,
  getMockSysMenuList,
  hasMenuChildren,
  softDeleteMenu,
} from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 软删菜单：有未删子节点 → 400；否则清绑定后软删本节点 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const exists = getMockSysMenuList().find((m) => m.id === id && m.deleted_at === 0);
  if (!exists) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `menu ${id} not found`);
  }

  if (hasMenuChildren(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "请先删除子菜单");
  }

  clearMenuApis(id);
  clearRoleMenusByMenuId(id);
  const removed = softDeleteMenu(id);
  return useResponseSuccess(toCamelRow(removed!));
});
