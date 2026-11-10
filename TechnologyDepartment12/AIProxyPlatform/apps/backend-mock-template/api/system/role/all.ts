import { defineEventHandler, getQuery } from "h3";
import { ensureUserSeeds, getMockSysRoleList } from "~/utils/mock-data";
import { toUserRoleCamelRow } from "~/utils/user-role-camel";
import { useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 全量角色（未软删），用于用户表单的角色下拉。
 * 附 status 过滤，按 sort/id 升序。
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const query = getQuery(event);
  const { status } = query;
  let items = getMockSysRoleList().filter((r) => r.deleted_at === 0);
  if (["0", "1"].includes(status as string)) {
    items = items.filter((r) => r.is_enabled === Number(status));
  }
  items.sort((a, b) => a.sort - b.sort || a.id - b.id);
  return useResponseSuccess(items.map(toUserRoleCamelRow));
});
