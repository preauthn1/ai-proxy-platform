import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureUserSeeds, softDeleteRole } from "~/utils/mock-data";
import { toUserRoleCamelRow } from "~/utils/user-role-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 软删角色：有关联用户/子角色 → 拒绝；否则清菜单/接口绑定后软删。 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const result = softDeleteRole(id);
  if (!result.ok) {
    setResponseStatus(event, result.reason.includes("not found") ? 404 : 400);
    return useResponseError("BadRequest", result.reason);
  }
  return useResponseSuccess(toUserRoleCamelRow(result.row));
});
