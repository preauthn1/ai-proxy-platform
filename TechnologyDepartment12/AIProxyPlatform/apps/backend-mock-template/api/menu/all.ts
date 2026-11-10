import { eventHandler } from "h3";
import { verifyAccessToken } from "~/utils/session-utils";
import { getUserRuntimeMenus } from "~/utils/menu-route-project";
import { unAuthorizedResponse, useResponseSuccess } from "~/utils/response";

/**
 * Runtime dynamic menus for both frontends.
 * Source of truth: sys_user_role → sys_role_menu → sys_menu (projected tree).
 */
export default eventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  const menus = getUserRuntimeMenus(userinfo.username, userinfo.id);
  return useResponseSuccess(menus);
});
