import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import { ensureUserSeeds, resetUserPassword } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 重置用户密码：{ password }。占位哈希，不真实加密。 */
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

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const password = String(raw.password ?? "").trim();
  if (!password) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "password is required");
  }

  const row = resetUserPassword(id, password);
  if (!row) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `user ${id} not found`);
  }
  return useResponseSuccess({ id: row.id });
});
