import { defineEventHandler } from "h3";
import { ensureMenuApiSeeds, getMockSysApiList } from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 全量接口（未软删），用于联动与同步对比 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();
  const items = getMockSysApiList()
    .filter((a) => a.deleted_at === 0)
    .sort((a, b) => a.id - b.id);
  return useResponseSuccess(items.map(toCamelRow));
});
