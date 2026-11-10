import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureUserSeeds, softDeleteUser } from "~/utils/mock-data";
import { toUserCamelRow } from "~/utils/user-role-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 软删用户（清 sys_user_role 关联）。 */
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

  const removed = softDeleteUser(id);
  if (!removed) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `user ${id} not found`);
  }
  return useResponseSuccess(toUserCamelRow(removed));
});
