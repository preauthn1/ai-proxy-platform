import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  ensureUserSeeds,
  getMockSysApiList,
  getRoleApiIds,
} from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 读取某角色可授权的接口列表（带 bound 标记）。
 * 复用 /system/api/all 的接口数据，按 role_api 标记 bound。
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

  const boundIds = new Set(getRoleApiIds(roleId));
  const items = getMockSysApiList()
    .filter((a) => a.deleted_at === 0)
    .sort((a, b) => a.id - b.id)
    .map((a) => ({ ...toCamelRow(a), bound: boundIds.has(a.id) }));

  return useResponseSuccess(items);
});
