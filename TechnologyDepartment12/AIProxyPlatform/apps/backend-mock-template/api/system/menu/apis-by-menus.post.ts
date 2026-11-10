import { defineEventHandler, readBody, setResponseStatus } from "h3";
import { ensureMenuApiSeeds, getApiIdsByMenuIds } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 按菜单 ID 列表聚合 sys_menu_api，返回去重的未软删 apiId。
 * 供角色授权抽屉「从已选菜单带出接口」一次拉取，避免 N 次 /menu/:id/apis。
 * body: { menuIds: number[] }
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const rawIds = raw.menuIds ?? raw.menu_ids;
  if (!Array.isArray(rawIds)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "menuIds must be an array");
  }
  const menuIds = (rawIds as unknown[])
    .map((n) => Number(n))
    .filter((n) => Number.isFinite(n) && n > 0);

  const apiIds = getApiIdsByMenuIds(menuIds);
  return useResponseSuccess({ menuIds, apiIds });
});
