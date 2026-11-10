import { defineEventHandler } from "h3";
import { API_SYNC_MANIFEST, ensureMenuApiSeeds, syncApisFromManifest } from "~/utils/mock-data";
import { useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 接口同步：按内置路由清单（API_SYNC_MANIFEST）upsert 进 sys_api。
 * 命中 (method, path) 则跳过，否则新增。返回 added/skipped/total。
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const result = syncApisFromManifest([...API_SYNC_MANIFEST]);
  return useResponseSuccess(result);
});
