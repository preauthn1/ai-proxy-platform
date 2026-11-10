import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  getMenuApiIds,
  getMockSysApiList,
  getMockSysMenuList,
} from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 读取某菜单已绑定的接口列表 */
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
  const menu = getMockSysMenuList().find((m) => m.id === id && m.deleted_at === 0);
  if (!menu) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `menu ${id} not found`);
  }

  const boundIds = new Set(getMenuApiIds(id));
  const apis = getMockSysApiList()
    .filter((a) => a.deleted_at === 0)
    .map((a) => ({ ...toCamelRow(a), bound: boundIds.has(a.id) }));
  return useResponseSuccess(apis);
});
