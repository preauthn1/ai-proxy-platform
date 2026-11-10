import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import { ensureMenuApiSeeds, getMockSysMenuList, setMenuApis } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 全量替换某菜单的接口绑定（覆盖写）。body: { apiIds: number[] } */
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

  const body = ((await readBody(event)) ?? {}) as { apiIds?: number[] | string[] };
  const rawIds = Array.isArray(body.apiIds) ? body.apiIds : [];
  const apiIds = rawIds.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);

  const bound = setMenuApis(id, apiIds);
  return useResponseSuccess({ menuId: id, apiIds: bound });
});
