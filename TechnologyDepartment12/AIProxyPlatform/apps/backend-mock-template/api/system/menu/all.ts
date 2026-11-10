import { defineEventHandler, getQuery } from "h3";
import { ensureMenuApiSeeds, getMockSysMenuList, type SysMenu } from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 全量菜单（未软删），用于父菜单下拉与前端组树 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const query = getQuery(event);
  const { type, status } = query;
  let items: SysMenu[] = getMockSysMenuList().filter((m) => m.deleted_at === 0);
  // 父菜单下拉通常只列 DIR/MENU（BUTTON 不能作父）
  if (type && type !== "全部") {
    items = items.filter((m) => m.type === type);
  }
  if (["0", "1"].includes(status as string)) {
    items = items.filter((m) => m.is_enabled === Number(status));
  }
  items.sort((a, b) => a.sort - b.sort || a.id - b.id);
  return useResponseSuccess(items.map(toCamelRow));
});
