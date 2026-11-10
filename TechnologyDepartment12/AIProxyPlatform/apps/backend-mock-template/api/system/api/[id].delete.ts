import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  clearApiMenus,
  clearRoleApisByApiId,
  ensureMenuApiSeeds,
  getMockSysApiList,
  softDeleteApi,
} from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 软删接口：清 sys_menu_api 绑定后软删 */
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

  const exists = getMockSysApiList().find((a) => a.id === id && a.deleted_at === 0);
  if (!exists) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `api ${id} not found`);
  }

  clearApiMenus(id);
  clearRoleApisByApiId(id);
  const removed = softDeleteApi(id);
  return useResponseSuccess(toCamelRow(removed!));
});
