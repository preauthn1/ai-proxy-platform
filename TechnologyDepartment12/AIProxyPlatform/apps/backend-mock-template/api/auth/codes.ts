import { eventHandler } from "h3";
import { verifyAccessToken } from "~/utils/session-utils";
import { getUserAccessCodes } from "~/utils/menu-route-project";
import { unAuthorizedResponse, useResponseSuccess } from "~/utils/response";

/**
 * Button / access codes for the current user.
 * Derived from granted BUTTON.permission_code (and BUTTON under granted MENU).
 */
export default eventHandler((event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  const codes = getUserAccessCodes(userinfo.username, userinfo.id);
  return useResponseSuccess(codes);
});
