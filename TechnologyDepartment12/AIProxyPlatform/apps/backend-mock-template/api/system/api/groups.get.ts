import { defineEventHandler } from "h3";
import { ensureMenuApiSeeds, getMockSysApiList } from "~/utils/mock-data";
import { useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 去重的 api_group 列表，供前端分组下拉 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();
  const groups = [
    ...new Set(
      getMockSysApiList()
        .filter((a) => a.deleted_at === 0 && a.api_group)
        .map((a) => a.api_group),
    ),
  ].sort();
  return useResponseSuccess(groups);
});
