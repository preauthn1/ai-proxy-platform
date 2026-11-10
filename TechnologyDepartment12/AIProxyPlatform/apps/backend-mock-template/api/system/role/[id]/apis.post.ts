import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  ensureUserSeeds,
  getMockSysApiList,
  getMockSysRoleList,
  setRoleApis,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 全量替换某角色的接口授权：{ apiIds: number[] }。 */
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
  const rawIds = raw.apiIds ?? raw.api_ids;
  if (!Array.isArray(rawIds)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "apiIds must be an array");
  }
  const apiIds = (rawIds as unknown[]).map((n) => Number(n)).filter((n) => Number.isFinite(n));
  const validApiIds = new Set(
    getMockSysApiList()
      .filter((a) => a.deleted_at === 0)
      .map((a) => a.id),
  );
  for (const aid of apiIds) {
    if (!validApiIds.has(aid)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `api ${aid} not found`);
    }
  }

  setRoleApis(roleId, apiIds);
  return useResponseSuccess({ roleId, apiIds });
});
