import { eventHandler } from "h3";
import { verifyAccessToken } from "~/utils/session-utils";
import { ensureUserSeeds, getMockSysUserList, getUserRoleCodes } from "~/utils/mock-data";
import { unAuthorizedResponse, useResponseSuccess } from "~/utils/response";

export default eventHandler((event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }

  ensureUserSeeds();
  const sysUser = getMockSysUserList().find(
    (item) => item.username === userinfo.username && item.deleted_at === 0,
  );
  if (!sysUser) {
    return unAuthorizedResponse(event);
  }

  const roles = getUserRoleCodes(sysUser.id);

  return useResponseSuccess({
    id: sysUser.id,
    username: sysUser.username,
    realName: sysUser.nickname,
    roles,
    homePath: "/analytics",
  });
});
