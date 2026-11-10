import { defineEventHandler, getHeader, getRequestIP, readBody, setResponseStatus } from "h3";
import { createSession } from "~/utils/session-utils";
import { verifyAltchaPayload } from "~/utils/altcha";
import {
  accessBlockedBody,
  appendLoginLog,
  ensureUserSeeds,
  findBlockingHit,
  getMockSysUserList,
  getUserRoleCodes,
} from "~/utils/mock-data";
import { forbiddenResponse, useResponseError, useResponseSuccess } from "~/utils/response";

function clientMeta(event: Parameters<typeof getHeader>[0]) {
  const userAgent = getHeader(event, "user-agent") ?? "";
  const loginIp =
    getRequestIP(event, { xForwardedFor: true }) ??
    getHeader(event, "x-forwarded-for")?.split(",")[0]?.trim() ??
    "127.0.0.1";
  return { userAgent, loginIp };
}

export default defineEventHandler(async (event) => {
  ensureUserSeeds();

  const { password, username, altcha } = await readBody<{
    password?: string;
    username?: string;
    altcha?: string;
  }>(event);
  const { userAgent, loginIp } = clientMeta(event);

  if (!password || !username) {
    appendLoginLog({
      username: username ?? "",
      success: 0,
      reason: "Username and password are required",
      statusCode: 400,
      loginIp,
      userAgent,
    });
    setResponseStatus(event, 400);
    return useResponseError("Username and password are required", 1001);
  }

  // ALTCHA PoW 人机校验：未通过直接拒绝，不计入账号密码失败日志。
  const altchaOk = await verifyAltchaPayload(altcha ?? "");
  if (!altchaOk) {
    appendLoginLog({
      username,
      success: 0,
      reason: "ALTCHA verification failed",
      statusCode: 403,
      loginIp,
      userAgent,
    });
    return forbiddenResponse(event, "ALTCHA verification failed.");
  }

  const sharedList = getMockSysUserList();
  const sysUser = sharedList.find((item) => item.username === username && item.deleted_at === 0);

  // demo$bcrypt$ 前缀占位密码：提取后缀比对明文
  if (!sysUser || !sysUser.password_hash.endsWith(password)) {
    appendLoginLog({
      username,
      success: 0,
      reason: "Username or password is incorrect.",
      statusCode: 401,
      loginIp,
      userAgent,
    });
    setResponseStatus(event, 401);
    // 与 java-admin AUTH_INVALID_CREDENTIALS(2002) 对齐
    return useResponseError("Username or password is incorrect.", 2002);
  }

  // LOGIN + USER：凭证正确后、发 token 前查黑名单（对齐 Java AuthService）
  const userHit = findBlockingHit("USER", String(sysUser.id), "LOGIN");
  if (userHit) {
    console.warn(
      `[BLACKLIST] Access Blocked targetType=USER targetValue=${sysUser.id} scene=LOGIN hitScope=${userHit.scope} reason=${userHit.reason}`,
    );
    appendLoginLog({
      username: sysUser.username,
      success: 0,
      reason: "Access Blocked",
      statusCode: 403,
      sysUserId: sysUser.id,
      loginIp,
      userAgent,
    });
    setResponseStatus(event, 403);
    return accessBlockedBody();
  }

  appendLoginLog({
    username: sysUser.username,
    success: 1,
    reason: "",
    statusCode: 200,
    sysUserId: sysUser.id,
    loginIp,
    userAgent,
  });

  const roles = getUserRoleCodes(sysUser.id);

  // 对齐 java-admin：每次登录生成会话专属 RSA，返回 publicKey 供前端后续加密
  const { accessToken, publicKey } = createSession({
    id: sysUser.id,
    username: sysUser.username,
  });

  return useResponseSuccess({
    id: sysUser.id,
    username: sysUser.username,
    realName: sysUser.nickname,
    roles,
    homePath: "/analytics",
    accessToken,
    publicKey,
  });
});
